package scalan.primitives

import scala.annotation.unchecked.uncheckedVariance
import scalan._
import scala.reflect.runtime.universe._
import scalan.common.OverloadHack.{Overloaded2, Overloaded1}
import scala.reflect.runtime.universe.{WeakTypeTag, weakTypeTag}
import scalan.meta.ScalanAst._

package impl {
// Abs -----------------------------------
trait StructItemsAbs extends StructItems {
  self: StructsDsl with Scalan =>

  // single proxy for each type family
  implicit def proxyStructItem[Val, Schema <: Struct](p: Rep[StructItem[Val, Schema]]): StructItem[Val, Schema] = {
    proxyOps[StructItem[Val, Schema]](p)(scala.reflect.classTag[StructItem[Val, Schema]])
  }

  // familyElem
  class StructItemElem[Val, Schema <: Struct, To <: StructItem[Val, Schema]](implicit _eVal: Elem[Val @uncheckedVariance], _eSchema: Elem[Schema])
    extends EntityElem[To] {
    def eVal = _eVal
    def eSchema = _eSchema
    lazy val parent: Option[Elem[_]] = None
    lazy val typeArgs = TypeArgs("Val" -> eVal, "Schema" -> eSchema)
    override def isEntityType = true
    override lazy val tag = {
      implicit val tagAnnotatedVal = eVal.tag
      implicit val tagSchema = eSchema.tag
      weakTypeTag[StructItem[Val, Schema]].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      implicit val eTo: Elem[To] = this
      val conv = fun {x: Rep[StructItem[Val, Schema]] => convertStructItem(x) }
      tryConvert(element[StructItem[Val, Schema]], this, x, conv)
    }

    def convertStructItem(x: Rep[StructItem[Val, Schema]]): Rep[To] = {
      x.selfType1 match {
        case _: StructItemElem[_, _, _] => x.asRep[To]
        case e => !!!(s"Expected $x to have StructItemElem[_, _, _], but got $e", x)
      }
    }

    override def getDefaultRep: Rep[To] = ???
  }

  implicit def structItemElement[Val, Schema <: Struct](implicit eVal: Elem[Val @uncheckedVariance], eSchema: Elem[Schema]): Elem[StructItem[Val, Schema]] =
    cachedElem[StructItemElem[Val, Schema, StructItem[Val, Schema]]](eVal, eSchema)

  implicit case object StructItemCompanionElem extends CompanionElem[StructItemCompanionAbs] {
    lazy val tag = weakTypeTag[StructItemCompanionAbs]
    protected def getDefaultRep = StructItem
  }

  abstract class StructItemCompanionAbs extends CompanionDef[StructItemCompanionAbs] {
    def selfType = StructItemCompanionElem
    override def toString = "StructItem"
  }
  def StructItem: Rep[StructItemCompanionAbs]
  implicit def proxyStructItemCompanionAbs(p: Rep[StructItemCompanionAbs]): StructItemCompanionAbs =
    proxyOps[StructItemCompanionAbs](p)

  abstract class AbsStructItemBase[Val, Schema <: Struct]
      (key: Rep[StructKey[Schema]], value: Rep[Val])(implicit eVal: Elem[Val], eSchema: Elem[Schema])
    extends StructItemBase[Val, Schema](key, value) with Def[StructItemBase[Val, Schema]] {
    lazy val selfType = element[StructItemBase[Val, Schema]]
  }
  // elem for concrete class
  class StructItemBaseElem[Val, Schema <: Struct](val iso: Iso[StructItemBaseData[Val, Schema], StructItemBase[Val, Schema]])(implicit override val eVal: Elem[Val], override val eSchema: Elem[Schema])
    extends StructItemElem[Val, Schema, StructItemBase[Val, Schema]]
    with ConcreteElem[StructItemBaseData[Val, Schema], StructItemBase[Val, Schema]] {
    override lazy val parent: Option[Elem[_]] = Some(structItemElement(element[Val], element[Schema]))
    override lazy val typeArgs = TypeArgs("Val" -> eVal, "Schema" -> eSchema)

    override def convertStructItem(x: Rep[StructItem[Val, Schema]]) = StructItemBase(x.key, x.value)
    override def getDefaultRep = StructItemBase(element[StructKey[Schema]].defaultRepValue, element[Val].defaultRepValue)
    override lazy val tag = {
      implicit val tagVal = eVal.tag
      implicit val tagSchema = eSchema.tag
      weakTypeTag[StructItemBase[Val, Schema]]
    }
  }

  // state representation type
  type StructItemBaseData[Val, Schema <: Struct] = (StructKey[Schema], Val)

  // 3) Iso for concrete class
  class StructItemBaseIso[Val, Schema <: Struct](implicit eVal: Elem[Val], eSchema: Elem[Schema])
    extends EntityIso[StructItemBaseData[Val, Schema], StructItemBase[Val, Schema]] with Def[StructItemBaseIso[Val, Schema]] {
    override def from(p: Rep[StructItemBase[Val, Schema]]) =
      (p.key, p.value)
    override def to(p: Rep[(StructKey[Schema], Val)]) = {
      val Pair(key, value) = p
      StructItemBase(key, value)
    }
    lazy val eFrom = pairElement(element[StructKey[Schema]], element[Val])
    lazy val eTo = new StructItemBaseElem[Val, Schema](self)
    lazy val selfType = new StructItemBaseIsoElem[Val, Schema](eVal, eSchema)
    def productArity = 2
    def productElement(n: Int) = (eVal, eSchema).productElement(n)
  }
  case class StructItemBaseIsoElem[Val, Schema <: Struct](eVal: Elem[Val], eSchema: Elem[Schema]) extends Elem[StructItemBaseIso[Val, Schema]] {
    def isEntityType = true
    def getDefaultRep = reifyObject(new StructItemBaseIso[Val, Schema]()(eVal, eSchema))
    lazy val tag = {
      implicit val tagVal = eVal.tag
      implicit val tagSchema = eSchema.tag
      weakTypeTag[StructItemBaseIso[Val, Schema]]
    }
    lazy val typeArgs = TypeArgs("Val" -> eVal, "Schema" -> eSchema)
  }
  // 4) constructor and deconstructor
  class StructItemBaseCompanionAbs extends CompanionDef[StructItemBaseCompanionAbs] {
    def selfType = StructItemBaseCompanionElem
    override def toString = "StructItemBase"
    @scalan.OverloadId("fromData")
    def apply[Val, Schema <: Struct](p: Rep[StructItemBaseData[Val, Schema]])(implicit eVal: Elem[Val], eSchema: Elem[Schema]): Rep[StructItemBase[Val, Schema]] =
      isoStructItemBase(eVal, eSchema).to(p)
    @scalan.OverloadId("fromFields")
    def apply[Val, Schema <: Struct](key: Rep[StructKey[Schema]], value: Rep[Val])(implicit eVal: Elem[Val], eSchema: Elem[Schema]): Rep[StructItemBase[Val, Schema]] =
      mkStructItemBase(key, value)

    def unapply[Val, Schema <: Struct](p: Rep[StructItem[Val, Schema]]) = unmkStructItemBase(p)
  }
  lazy val StructItemBaseRep: Rep[StructItemBaseCompanionAbs] = new StructItemBaseCompanionAbs
  lazy val StructItemBase: StructItemBaseCompanionAbs = proxyStructItemBaseCompanion(StructItemBaseRep)
  implicit def proxyStructItemBaseCompanion(p: Rep[StructItemBaseCompanionAbs]): StructItemBaseCompanionAbs = {
    proxyOps[StructItemBaseCompanionAbs](p)
  }

  implicit case object StructItemBaseCompanionElem extends CompanionElem[StructItemBaseCompanionAbs] {
    lazy val tag = weakTypeTag[StructItemBaseCompanionAbs]
    protected def getDefaultRep = StructItemBase
  }

  implicit def proxyStructItemBase[Val, Schema <: Struct](p: Rep[StructItemBase[Val, Schema]]): StructItemBase[Val, Schema] =
    proxyOps[StructItemBase[Val, Schema]](p)

  implicit class ExtendedStructItemBase[Val, Schema <: Struct](p: Rep[StructItemBase[Val, Schema]])(implicit eVal: Elem[Val], eSchema: Elem[Schema]) {
    def toData: Rep[StructItemBaseData[Val, Schema]] = isoStructItemBase(eVal, eSchema).from(p)
  }

  // 5) implicit resolution of Iso
  implicit def isoStructItemBase[Val, Schema <: Struct](implicit eVal: Elem[Val], eSchema: Elem[Schema]): Iso[StructItemBaseData[Val, Schema], StructItemBase[Val, Schema]] =
    reifyObject(new StructItemBaseIso[Val, Schema]()(eVal, eSchema))

  // 6) smart constructor and deconstructor
  def mkStructItemBase[Val, Schema <: Struct](key: Rep[StructKey[Schema]], value: Rep[Val])(implicit eVal: Elem[Val], eSchema: Elem[Schema]): Rep[StructItemBase[Val, Schema]]
  def unmkStructItemBase[Val, Schema <: Struct](p: Rep[StructItem[Val, Schema]]): Option[(Rep[StructKey[Schema]], Rep[Val])]

  registerModule(StructItems_Module)
}

// Std -----------------------------------
trait StructItemsStd extends StructItemsDsl {
  self: StructsDsl with ScalanStd =>
  lazy val StructItem: Rep[StructItemCompanionAbs] = new StructItemCompanionAbs {
  }

  case class StdStructItemBase[Val, Schema <: Struct]
      (override val key: Rep[StructKey[Schema]], override val value: Rep[Val])(implicit eVal: Elem[Val], eSchema: Elem[Schema])
    extends AbsStructItemBase[Val, Schema](key, value) {
  }

  def mkStructItemBase[Val, Schema <: Struct]
    (key: Rep[StructKey[Schema]], value: Rep[Val])(implicit eVal: Elem[Val], eSchema: Elem[Schema]): Rep[StructItemBase[Val, Schema]] =
    new StdStructItemBase[Val, Schema](key, value)
  def unmkStructItemBase[Val, Schema <: Struct](p: Rep[StructItem[Val, Schema]]) = p match {
    case p: StructItemBase[Val, Schema] @unchecked =>
      Some((p.key, p.value))
    case _ => None
  }
}

// Exp -----------------------------------
trait StructItemsExp extends StructItemsDsl {
  self: StructsDsl with ScalanExp =>
  lazy val StructItem: Rep[StructItemCompanionAbs] = new StructItemCompanionAbs {
  }

  case class ExpStructItemBase[Val, Schema <: Struct]
      (override val key: Rep[StructKey[Schema]], override val value: Rep[Val])(implicit eVal: Elem[Val], eSchema: Elem[Schema])
    extends AbsStructItemBase[Val, Schema](key, value)

  object StructItemBaseMethods {
  }

  def mkStructItemBase[Val, Schema <: Struct]
    (key: Rep[StructKey[Schema]], value: Rep[Val])(implicit eVal: Elem[Val], eSchema: Elem[Schema]): Rep[StructItemBase[Val, Schema]] =
    new ExpStructItemBase[Val, Schema](key, value)
  def unmkStructItemBase[Val, Schema <: Struct](p: Rep[StructItem[Val, Schema]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: StructItemBaseElem[Val, Schema] @unchecked =>
      Some((p.asRep[StructItemBase[Val, Schema]].key, p.asRep[StructItemBase[Val, Schema]].value))
    case _ =>
      None
  }

  object StructItemMethods {
    object key {
      def unapply(d: Def[_]): Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[StructItemElem[_, _, _]] && method.getName == "key" =>
          Some(receiver).asInstanceOf[Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object value {
      def unapply(d: Def[_]): Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[StructItemElem[_, _, _]] && method.getName == "value" =>
          Some(receiver).asInstanceOf[Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[StructItem[Val, Schema]] forSome {type Val; type Schema <: Struct}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }
}

object StructItems_Module extends scalan.ModuleInfo {
  val dump = "H4sIAAAAAAAAALVWTWwbRRQeO3Yc21FJy0+hCBpSFxAUu6KHIkVVlTQOanGdKFtaZCrQePfZ2WZ2d9gdR2sORXDogZ8LQhyQOFQCcamQUG89IA4gIYSQ4MqZUwlCPdATiDezv3azSamED6PZ2ef3873vfTvXtkjRc8mTnk4ZtesWCFrX1H7BEzWtaQtTDM86xoDBEvR+fvYbp/LO++08memQyXXqLXmsQ8rBpunzeK8Jo0XK1NbBE47rCfJES0Vo6A5joAvTsRumZQ0E7TJotExPzLdIoesYwzfIZZJrkRndsXUXBGinGPU88MLzKZAZmfFzWT0PV3gSw27IKhqpKs651BSYPsaYCezXgGtD27GHliB7wtRWuEwLbargc6zhtMWZCjPRIiXT4o4roqgljLDuGNFjwaZ4QPa1LtFN2sCo/YYmXNPuS2ec6hu0D200keYFrMED1js35BA6r3rCGInnc0IIduV5lVg9waweY1aXmNU0cE3KzDepfLnqOv6QBL/cBCE+RxdHdnEReYCmbdTevai/elurWnn5Z1+mUlIJTaKjgxkMUe1BbL9f+9C79eLV43lS6ZCK6S10PeFSXaRpEMJVpbbtCJVzjCB1+9jBuawOqigLaDNGk7LuWJza6CnEchobxUzdFNJYnk2H7cnAviQ4RKY5n+fiemcz6lVcOkUZW735yHOHf2++kif50RBldKnhMLiRU0EqSIeBLk4LsMIAcr1PkInzlCU448Gkpq+DRdWZXMp+spZ2SCsG6KmbfxjfHSUX8zGsYRZ310l0se+FT24chtUv82Sqo4i/zGhf9VTitgSe3iFTzia4wXlpkzK527avJQN6dMBEiHYapgmESZDZzJnlIDGcV7OQi8qvBnRuOzbUlldrf2k/fHRNstUl08GbYIj/MY///euenlBERpQ3YBgjjqM/2oNy0JyXQpvJ3Vqh3h+Is5LLQUGKCMMAsqPEnb6jq8qBMtuf+suB3FiAAqCHyFuhyTKplM5y3EkJgqJ28JNVNgplwuJF6kGSt2TVY1nzoubrobXWA2zr5Nd5UjxDij2ki9cixa4zsI1ocFHwBfhiMTrLjdIFB5W61IrYHMjcLFFJqFzHh2kxMsHkKgE5NMeCvXO3zNeuvifUfOT8UdVe6V5ClZxXFT+eAKLKjgEp7UKNnWYs+hJ9deXKg39+9vr9Si2nuqawKK8d/Q9aGUnb/6iFZIxfmPboyZ2q5pJDO9AgqASMQ9ff3jrRPfKBUs6iKjDRBLV9VE7t3oGNKOsbYJyn+JGSwNyLapZTH7L9SRMVcUalOU3ohEG5uyOZfFq+B8rItZ1EXkYM6xkYLoHOqAuGvCiAhReZgBbHPj554czDF15WjJ42lFHwJpbX7a9dZymfV5eEp3e4JKBRrWlxvATi5ti3J35568cvPle6mq6umqICdi4sgLumhZe1TfDi2uYyatNCKiLJLt/+tP3MT9d/U/SoSFKj2tvx3SthsM+35aO8f46zQ8ZJQY/CJ7mfatq6XNi/X+Ipdg4LAAA="
}
}

