import akka.typed._
import akka.typed.ScalaDSL._
import akka.typed.AskPattern._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.util._

object HelloWorld {
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String)
 
  val greeter = Static[Greet] { msg ⇒
    println(s"Hello ${msg.whom}!")
    msg.replyTo ! Greeted(msg.whom)
  }
}


object App {
  def main(args: Array[String]) = {
  	import HelloWorld._
	// using global pool since we want to run tasks after system shutdown
	import scala.concurrent.ExecutionContext.Implicits.global
	
	implicit val timeout = Timeout(3 seconds)

	val system: ActorSystem[Greet] = ActorSystem("hello", Props(greeter))
	
	val future: Future[Greeted] = system ? (Greet("world", _))
	 
	for {
	  greeting <- future.recover { case ex => ex.getMessage }
	  done <- { println(s"result: $greeting"); system.terminate() }
	} println("system terminated")
  }
}