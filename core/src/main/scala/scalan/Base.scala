package scalan

import java.util.{Arrays, Objects, Properties}
import java.io.FileReader

import com.typesafe.scalalogging.slf4j.LazyLogging

import scala.annotation.unchecked.uncheckedVariance

trait Base extends LazyLogging { self: Scalan =>
  type |[+A, +B] = Either[A, B]
  type Rep[+A]
  type Def[+A]
  type IntRep = Rep[Int]
  type BoolRep = Rep[Boolean]
  type UnitRep = Rep[Unit]
  type NothingRep = Rep[Nothing]
  type ByteRep = Rep[Byte]
  type ShortRep = Rep[Short]
  type CharRep = Rep[Char]
  type LongRep = Rep[Long]
  type FloatRep = Rep[Float]
  type DoubleRep = Rep[Double]
  type :=>[-A, +B] = PartialFunction[A, B]

  class StagingException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
    def this(message: String) = this(message, null)
  }

  class ElemException[A](message: String)(implicit val element: Elem[A]) extends StagingException(message)

  def ??? : Nothing = ???("not implemented")
  def ???(value: Any): Nothing = throw new NotImplementedError(value.toString)
  def ???(msg: String, syms: Rep[_]*): Nothing = throw new StagingException(msg + " " + syms.mkString)

  def !!! : Nothing = !!!("should not be called")
  def !!!(msg: String): Nothing = throw new StagingException(msg)
  def !!!(msg: String, syms: Rep[_]*): Nothing = throw new StagingException(msg + " " + syms.mkString)
  def !!!(msg: String, e: Throwable): Nothing = throw new StagingException(msg, e)

  var isDebug: Boolean = Base.config.getProperty("scalan.debug") != null

  implicit class RepForSomeExtension(x: Rep[_]) {
    def asRep[T]: Rep[T] = x.asInstanceOf[Rep[T]]
  }

  def toRep[A](x: A)(implicit eA: Elem[A]): Rep[A] = !!!(s"Don't know how to create Rep for $x with element $eA")
  implicit def liftToRep[A:Elem](x: A): Rep[A] = toRep(x)

  type RReifiable[+T] = Rep[Reifiable[T]]
  trait Reifiable[+T] extends Product {
    def selfType: Elem[T @uncheckedVariance]
    def self: Rep[T]

    override def equals(other: Any) = other match {
      // check that nodes correspond to same operation, have the same type, and the same arguments
      // alternative would be to include Elem fields into case class
      case other: Reifiable[_] =>
        getClass == other.getClass && selfType.name == other.selfType.name &&
          productArity == other.productArity && {
          val len = productArity
          var i = 0
          var result = true
          while (result && i < len) {
            result = Objects.deepEquals(productElement(i), other.productElement(i))
            i += 1
          }
          result
        }
      case _ => false
    }

    override lazy val hashCode = {
      val len = productArity
      var i = 0
      var result = 1
      while (i < len) {
        val element = productElement(i)
        val elementHashCode = element match {
          case null => 0
          case arr: Array[Object] => Arrays.deepHashCode(arr)
          case arr: Array[Int] => Arrays.hashCode(arr)
          case arr: Array[Long] => Arrays.hashCode(arr)
          case arr: Array[Float] => Arrays.hashCode(arr)
          case arr: Array[Double] => Arrays.hashCode(arr)
          case arr: Array[Boolean] => Arrays.hashCode(arr)
          case arr: Array[Byte] => Arrays.hashCode(arr)
          case arr: Array[Short] => Arrays.hashCode(arr)
          case arr: Array[Char] => Arrays.hashCode(arr)
          case _ => element.hashCode
        }
        result = 41 * result + elementHashCode
        i += 1
      }
      result
    }

    override def toString = {
      val sb = new StringBuilder
      sb.append(productPrefix)
      sb.append("(")
      val iterator = productIterator
      if (iterator.hasNext) {
        append(sb, iterator.next)
      }
      while (iterator.hasNext) {
        sb.append(", ")
        append(sb, iterator.next)
      }
      sb.append(")")
      sb.toString
    }

    private final def append(sb: StringBuilder, x: Any): Unit = {
      x match {
        case arr: Array[_] =>
          sb.append("Array(")
          if (arr.length > 0) {
            append(sb, arr(0))
            var i = 1
            while (i < arr.length) {
              sb.append(", ")
              append(sb, arr(i))
              i += 1
            }
          }
          sb.append(")")
        case _ => sb.append(x)
      }
    }
  }

  abstract class CompanionBase[T] extends Reifiable[T] {
    override def productArity = 0
    override def productElement(n: Int) = ???
    override def canEqual(other: Any) = other.isInstanceOf[CompanionBase[_]]
  }

  // this is a bit hackish. Better would be to make Elem part of Rep in sequential context
  implicit class RepReifiable[T <: Reifiable[_]](x: Rep[T]) {
    def selfType1: Elem[T] = repReifiable_getElem(x)
  }
  def repReifiable_getElem[T <: Reifiable[_]](x: Rep[T]): Elem[T]

  object Def {
    def unapply[T](e: Rep[T]): Option[Def[T]] = def_unapply(e)
  }

  def def_unapply[T](e: Rep[T]): Option[Def[T]]
}

object Base {
  lazy val config = {
    val prop = new Properties
    try {
      val reader = new FileReader("scalan.properties")
      try {
        prop.load(reader)
      } finally {
        reader.close()
      }
    } catch {
      case _: Throwable => {}
    }
    prop.putAll(System.getProperties)
    prop
  }
}