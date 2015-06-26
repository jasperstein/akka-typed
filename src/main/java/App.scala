import akka.typed._
import akka.typed.ScalaDSL._
import akka.typed.AskPattern._
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util._
import scala.util.Random

object Behaviours {

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

	sealed trait CheckoutRequest
	case class Checkout(basket: Basket) extends CheckoutRequest

	sealed trait CheckoutResponse
	case class OutOfStock(basket: Basket) extends CheckoutResponse
	case object Ok extends CheckoutResponse

	type Stock = List[BasketItem]

//	val closeSession: Behavior[CloseSession] = Static[CloseSession] { case CloseSession(id) => {
//
//	}}

	def shopperBehaviour(sessions: Set[SessionId]): Behavior[SessionCommand] = ContextAware { ctx =>
		Total {
			case CloseSession(id) => {
				shopperBehaviour(sessions - id)
			}
			case OpenSession(replyTo) => {
				val sessionId = Random.nextInt()
				val basketActor = ctx.spawn(Props(basketBehaviour(List.empty)), s"basket-${sessionId}")
				val session = Session(sessionId, basketActor)
				replyTo ! session
				shopperBehaviour(sessions + session.id)
			}
		}
	}

	def basketBehaviour(basket: Basket): Behavior[BasketRequest] = Total {
		case Add(item) => basketBehaviour(basket :+ item)
		case Remove(productName) => basketBehaviour(basket.filterNot(_.productName == productName))
		case Empty => basketBehaviour(List.empty)
		case Get(replyTo) => replyTo ! basket ; Same
	}

	lazy val checkoutBehaviour = ???
}


object App {
	import Behaviours._
	// using global pool since we want to run tasks after system shutdown
	import scala.concurrent.ExecutionContext.Implicits.global

	implicit val timeout = Timeout(3 seconds)

	def main(args: Array[String]) = {
		val system: ActorSystem[SessionCommand] = ActorSystem("shopper", Props(shopperBehaviour(Set.empty)))

		val future = system ? OpenSession

		for {
			Session(id, basket) <- future.recover { case ex => ex.getMessage }
			_ <- Future { println("id: ", id, "basket: ", basket) }

			_ <- Future { basket ! Add(BasketItem("macbook", 1, 1000)) ; Thread.sleep(1000) }

			items <- basket ? Get

			_ <- Future { println("id: ", id, "basket: ", items) }

			done <- { system.terminate() }
		} println("system terminated")
  }
}
