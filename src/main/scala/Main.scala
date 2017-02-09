import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

import scala.concurrent.ExecutionContext.Implicits.global

object Main {

  implicit val actorSystem = ActorSystem("actor-system")

  case class Subscribe(i: ActorRef)
  case class BroadcastMessage(msg: String)

  class Router extends Actor {
    var connections = new ListBuffer[ActorRef]()

    def receive = {
      case Subscribe(i) => {
        println("Subscribed a client")
        connections.append(i)
      }
      case BroadcastMessage(msg) => connections.foreach(c => c ! Write(ByteString(msg)))
      case Received(data) => {
        println(s"Received data: ${data.utf8String}")
        receivedMessage(data, sender())
      }
      case PeerClosed => {
        println("Client disconnected.")
        connections = connections.filter(_ != sender())
      }
    }

    def receivedMessage(message: ByteString, from: ActorRef) = {
      connections.filter(_ != from).foreach(_ ! Write(ByteString(s"New message: ${message.utf8String}")))
      from ! Write(ByteString(s"Received: ${message.utf8String}"))
    }
  }

  class TcpListener(address: InetSocketAddress) extends Actor {

    IO(Tcp) ! Bind(self, address)

    def receive = {
      case b @ Bound(localAddress) => {
        println(s"Server started listening on ${b.localAddress}")
      }
      case CommandFailed(_: Bind) => {
        println("Server stopping.")
        context stop self
      }
      case c @ Connected(remote, local) => {
        println("New client connected!")
        val connection = sender()
        router ! Subscribe(connection)
        connection ! Register(router)
      }
      case Received(data) => {
        println(s"Received data: $data")
      }
    }
  }

  val router = actorSystem.actorOf(Props[Router])

  def main(args: Array[String]): Unit = {
    val listener = actorSystem.actorOf(Props(new TcpListener(new InetSocketAddress("localhost", 9999))))

    actorSystem.scheduler.schedule(60 seconds, 60 seconds) {
      router ! BroadcastMessage("Server says: Ping?\n")
    }
  }
}
