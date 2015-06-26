import akka.typed._
import akka.typed.ScalaDSL._
import akka.typed.AskPattern._
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util._
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

object Behaviours {

  case class Start(replyTo: ActorRef[System])
  case class System(sessionHandler: ActorRef[SessionCommand])

	type SessionId = Int

	sealed trait SessionCommand
	case class OpenSession(replyTo: ActorRef[Session]) extends SessionCommand
	case class CloseSession(id: SessionId) extends SessionCommand

	case class Session(id: SessionId, basket: ActorRef[BasketRequest])

	type Basket = List[BasketItem]
	case class BasketItem(productName: String, price: Int, quantity: Int)

	sealed trait BasketRequest
	final case class Add(item: BasketItem) extends BasketRequest
	final case class Remove(productName: String) extends BasketRequest
	final case class Get(replyTo: ActorRef[Basket]) extends BasketRequest
	case object Empty extends BasketRequest
  case class DoCheckout(replyTo: ActorRef[CheckoutResponse]) extends BasketRequest

	sealed trait CheckoutRequest
	case class Checkout(id: SessionId, basket: Basket, replyTo: ActorRef[CheckoutResponse]) extends CheckoutRequest

	sealed trait CheckoutResponse
	case class OutOfStock(basket: Basket) extends CheckoutResponse
	case object Ok extends CheckoutResponse

	type Stock = List[BasketItem]

  type CheckoutHandler = ActorRef[CheckoutRequest]

  val mainBehaviour: Behavior[Start] = ContextAware { ctx =>
    Static {
      case Start(replyTo) => {
        implicit val checkoutHandler = ctx.spawn(Props(checkoutBehaviour), "checkout")
        replyTo ! System(
          ctx.spawn(Props(shopperBehaviour(Map.empty)), "shopper")
        )
      }
    }
  }

	def shopperBehaviour(sessions: Map[SessionId, Session])(implicit ckHandler: CheckoutHandler): Behavior[SessionCommand] = ContextAware { ctx =>
		Total {
			case CloseSession(id) => {
        ctx.stop(sessions(id).basket)
				shopperBehaviour(sessions - id)
			}
			case OpenSession(replyTo) => {
				implicit val sessionId = Random.nextInt()
				val basketActor = ctx.spawn(Props(basketBehaviour(List.empty)), s"basket-${sessionId}")
				val session = Session(sessionId, basketActor)
				replyTo ! session
				shopperBehaviour(sessions + (session.id -> session))
			}
		}
	}

	def basketBehaviour(basket: Basket)(implicit ckHandler: CheckoutHandler, sessionId: SessionId): Behavior[BasketRequest] = ContextAware { ctx =>
    SelfAware { self =>
      Total {
        case Add(item) => basketBehaviour(basket :+ item)
        case Remove(productName) => basketBehaviour(basket.filterNot(_.productName == productName))
        case Empty => basketBehaviour(List.empty)
        case Get(replyTo) => replyTo ! basket; Same
        case DoCheckout(replyTo) => {
          implicit val timeout = Timeout(3 seconds)
          (ckHandler ? (Checkout(sessionId, basket, _: ActorRef[CheckoutResponse]))).foreach {
            case Ok => self ! Empty ; replyTo ! Ok
            case msg => replyTo ! msg
          }
          Same
        }
      }
    }
  }

	lazy val checkoutBehaviour: Behavior[CheckoutRequest] = Static {
    case Checkout(id, items, replyTo) => {
      println("Checkout - id: ", id, "basket: ", items)
      replyTo ! Ok
    }
  }
}


object App {
	import Behaviours._
	// using global pool since we want to run tasks after system shutdown

	implicit val timeout = Timeout(3 seconds)

	def main(args: Array[String]) = {
		val system: ActorSystem[Start] = ActorSystem("main", Props(mainBehaviour))

		val future: Future[System] = system ? Start

		for {
			System(sessionHandler) <- future.recover { case ex => ex.getMessage }

      Session(id, basket) <- sessionHandler ? OpenSession
			_ <- Future { println("id: ", id, "basket: ", basket) }

			_ <- Future { basket ! Add(BasketItem("macbook", 1, 1000)) ; Thread.sleep(1000) }

			items <- basket ? Get

			_ <- Future { println("id: ", id, "basket: ", items) }

      response <- basket ? DoCheckout

			done <- { system.terminate() }
		} {
      println("Checkout", response)
      println("system terminated")
    }
  }
}
