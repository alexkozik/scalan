package scalan.monads

import scala.reflect.runtime.universe._
import scalan._
import scalan.monads._
import scala.reflect.runtime.universe.{WeakTypeTag, weakTypeTag}
import scalan.meta.ScalanAst._

package impl {
// Abs -----------------------------------
trait AuthenticationsAbs extends scalan.ScalanDsl with Authentications {
  self: AuthenticationsDsl =>

  // single proxy for each type family
  implicit def proxyAuth[A](p: Rep[Auth[A]]): Auth[A] = {
    proxyOps[Auth[A]](p)(scala.reflect.classTag[Auth[A]])
  }

  // familyElem
  class AuthElem[A, To <: Auth[A]](implicit _eA: Elem[A])
    extends EntityElem[To] {
    def eA = _eA
    lazy val parent: Option[Elem[_]] = None
    lazy val typeArgs = scala.collection.immutable.ListMap[String, TypeDesc]("A" -> eA)
    override def isEntityType = true
    override lazy val tag = {
      implicit val tagA = eA.tag
      weakTypeTag[Auth[A]].asInstanceOf[WeakTypeTag[To]]
    }
    override def convert(x: Rep[Def[_]]) = {
      implicit val eTo: Elem[To] = this
      val conv = fun {x: Rep[Auth[A]] => convertAuth(x) }
      tryConvert(element[Auth[A]], this, x, conv)
    }

    def convertAuth(x: Rep[Auth[A]]): Rep[To] = {
      x.selfType1 match {
        case _: AuthElem[_, _] => x.asRep[To]
        case e => !!!(s"Expected $x to have AuthElem[_, _], but got $e", x)
      }
    }

    override def getDefaultRep: Rep[To] = ???
  }

  implicit def authElement[A](implicit eA: Elem[A]): Elem[Auth[A]] =
    cachedElem[AuthElem[A, Auth[A]]](eA)

  implicit case object AuthCompanionElem extends CompanionElem[AuthCompanionAbs] {
    lazy val tag = weakTypeTag[AuthCompanionAbs]
    protected def getDefaultRep = Auth
  }

  abstract class AuthCompanionAbs extends CompanionDef[AuthCompanionAbs] with AuthCompanion {
    def selfType = AuthCompanionElem
    override def toString = "Auth"
  }
  def Auth: Rep[AuthCompanionAbs]
  implicit def proxyAuthCompanionAbs(p: Rep[AuthCompanionAbs]): AuthCompanionAbs =
    proxyOps[AuthCompanionAbs](p)

  abstract class AbsLogin
      (user: Rep[String], password: Rep[String])
    extends Login(user, password) with Def[Login] {
    lazy val selfType = element[Login]
  }
  // elem for concrete class
  class LoginElem(val iso: Iso[LoginData, Login])
    extends AuthElem[SOption[String], Login]
    with ConcreteElem[LoginData, Login] {
    override lazy val parent: Option[Elem[_]] = Some(authElement(sOptionElement(StringElement)))
    override lazy val typeArgs = scala.collection.immutable.ListMap[String, TypeDesc]()

    override def convertAuth(x: Rep[Auth[SOption[String]]]) = // Converter is not generated by meta
!!!("Cannot convert from Auth to Login: missing fields List(user, password)")
    override def getDefaultRep = Login("", "")
    override lazy val tag = {
      weakTypeTag[Login]
    }
  }

  // state representation type
  type LoginData = (String, String)

  // 3) Iso for concrete class
  class LoginIso
    extends EntityIso[LoginData, Login] with Def[LoginIso] {
    override def from(p: Rep[Login]) =
      (p.user, p.password)
    override def to(p: Rep[(String, String)]) = {
      val Pair(user, password) = p
      Login(user, password)
    }
    lazy val eFrom = pairElement(element[String], element[String])
    lazy val eTo = new LoginElem(self)
    lazy val selfType = new LoginIsoElem
    def productArity = 0
    def productElement(n: Int) = ???
  }
  case class LoginIsoElem() extends Elem[LoginIso] {
    def isEntityType = true
    def getDefaultRep = reifyObject(new LoginIso())
    lazy val tag = {
      weakTypeTag[LoginIso]
    }
  }
  // 4) constructor and deconstructor
  class LoginCompanionAbs extends CompanionDef[LoginCompanionAbs] with LoginCompanion {
    def selfType = LoginCompanionElem
    override def toString = "Login"
    @scalan.OverloadId("fromData")
    def apply(p: Rep[LoginData]): Rep[Login] =
      isoLogin.to(p)
    @scalan.OverloadId("fromFields")
    def apply(user: Rep[String], password: Rep[String]): Rep[Login] =
      mkLogin(user, password)

    def unapply(p: Rep[Auth[SOption[String]]]) = unmkLogin(p)
  }
  lazy val LoginRep: Rep[LoginCompanionAbs] = new LoginCompanionAbs
  lazy val Login: LoginCompanionAbs = proxyLoginCompanion(LoginRep)
  implicit def proxyLoginCompanion(p: Rep[LoginCompanionAbs]): LoginCompanionAbs = {
    proxyOps[LoginCompanionAbs](p)
  }

  implicit case object LoginCompanionElem extends CompanionElem[LoginCompanionAbs] {
    lazy val tag = weakTypeTag[LoginCompanionAbs]
    protected def getDefaultRep = Login
  }

  implicit def proxyLogin(p: Rep[Login]): Login =
    proxyOps[Login](p)

  implicit class ExtendedLogin(p: Rep[Login]) {
    def toData: Rep[LoginData] = isoLogin.from(p)
  }

  // 5) implicit resolution of Iso
  implicit def isoLogin: Iso[LoginData, Login] =
    reifyObject(new LoginIso())

  // 6) smart constructor and deconstructor
  def mkLogin(user: Rep[String], password: Rep[String]): Rep[Login]
  def unmkLogin(p: Rep[Auth[SOption[String]]]): Option[(Rep[String], Rep[String])]

  abstract class AbsHasPermission
      (user: Rep[String], password: Rep[String])
    extends HasPermission(user, password) with Def[HasPermission] {
    lazy val selfType = element[HasPermission]
  }
  // elem for concrete class
  class HasPermissionElem(val iso: Iso[HasPermissionData, HasPermission])
    extends AuthElem[Boolean, HasPermission]
    with ConcreteElem[HasPermissionData, HasPermission] {
    override lazy val parent: Option[Elem[_]] = Some(authElement(BooleanElement))
    override lazy val typeArgs = scala.collection.immutable.ListMap[String, TypeDesc]()

    override def convertAuth(x: Rep[Auth[Boolean]]) = // Converter is not generated by meta
!!!("Cannot convert from Auth to HasPermission: missing fields List(user, password)")
    override def getDefaultRep = HasPermission("", "")
    override lazy val tag = {
      weakTypeTag[HasPermission]
    }
  }

  // state representation type
  type HasPermissionData = (String, String)

  // 3) Iso for concrete class
  class HasPermissionIso
    extends EntityIso[HasPermissionData, HasPermission] with Def[HasPermissionIso] {
    override def from(p: Rep[HasPermission]) =
      (p.user, p.password)
    override def to(p: Rep[(String, String)]) = {
      val Pair(user, password) = p
      HasPermission(user, password)
    }
    lazy val eFrom = pairElement(element[String], element[String])
    lazy val eTo = new HasPermissionElem(self)
    lazy val selfType = new HasPermissionIsoElem
    def productArity = 0
    def productElement(n: Int) = ???
  }
  case class HasPermissionIsoElem() extends Elem[HasPermissionIso] {
    def isEntityType = true
    def getDefaultRep = reifyObject(new HasPermissionIso())
    lazy val tag = {
      weakTypeTag[HasPermissionIso]
    }
  }
  // 4) constructor and deconstructor
  class HasPermissionCompanionAbs extends CompanionDef[HasPermissionCompanionAbs] with HasPermissionCompanion {
    def selfType = HasPermissionCompanionElem
    override def toString = "HasPermission"
    @scalan.OverloadId("fromData")
    def apply(p: Rep[HasPermissionData]): Rep[HasPermission] =
      isoHasPermission.to(p)
    @scalan.OverloadId("fromFields")
    def apply(user: Rep[String], password: Rep[String]): Rep[HasPermission] =
      mkHasPermission(user, password)

    def unapply(p: Rep[Auth[Boolean]]) = unmkHasPermission(p)
  }
  lazy val HasPermissionRep: Rep[HasPermissionCompanionAbs] = new HasPermissionCompanionAbs
  lazy val HasPermission: HasPermissionCompanionAbs = proxyHasPermissionCompanion(HasPermissionRep)
  implicit def proxyHasPermissionCompanion(p: Rep[HasPermissionCompanionAbs]): HasPermissionCompanionAbs = {
    proxyOps[HasPermissionCompanionAbs](p)
  }

  implicit case object HasPermissionCompanionElem extends CompanionElem[HasPermissionCompanionAbs] {
    lazy val tag = weakTypeTag[HasPermissionCompanionAbs]
    protected def getDefaultRep = HasPermission
  }

  implicit def proxyHasPermission(p: Rep[HasPermission]): HasPermission =
    proxyOps[HasPermission](p)

  implicit class ExtendedHasPermission(p: Rep[HasPermission]) {
    def toData: Rep[HasPermissionData] = isoHasPermission.from(p)
  }

  // 5) implicit resolution of Iso
  implicit def isoHasPermission: Iso[HasPermissionData, HasPermission] =
    reifyObject(new HasPermissionIso())

  // 6) smart constructor and deconstructor
  def mkHasPermission(user: Rep[String], password: Rep[String]): Rep[HasPermission]
  def unmkHasPermission(p: Rep[Auth[Boolean]]): Option[(Rep[String], Rep[String])]

  registerModule(Authentications_Module)
}

// Std -----------------------------------
trait AuthenticationsStd extends scalan.ScalanDslStd with AuthenticationsDsl {
  self: AuthenticationsDslStd =>
  lazy val Auth: Rep[AuthCompanionAbs] = new AuthCompanionAbs {
  }

  case class StdLogin
      (override val user: Rep[String], override val password: Rep[String])
    extends AbsLogin(user, password) {
  }

  def mkLogin
    (user: Rep[String], password: Rep[String]): Rep[Login] =
    new StdLogin(user, password)
  def unmkLogin(p: Rep[Auth[SOption[String]]]) = p match {
    case p: Login @unchecked =>
      Some((p.user, p.password))
    case _ => None
  }

  case class StdHasPermission
      (override val user: Rep[String], override val password: Rep[String])
    extends AbsHasPermission(user, password) {
  }

  def mkHasPermission
    (user: Rep[String], password: Rep[String]): Rep[HasPermission] =
    new StdHasPermission(user, password)
  def unmkHasPermission(p: Rep[Auth[Boolean]]) = p match {
    case p: HasPermission @unchecked =>
      Some((p.user, p.password))
    case _ => None
  }
}

// Exp -----------------------------------
trait AuthenticationsExp extends scalan.ScalanDslExp with AuthenticationsDsl {
  self: AuthenticationsDslExp =>
  lazy val Auth: Rep[AuthCompanionAbs] = new AuthCompanionAbs {
  }

  case class ExpLogin
      (override val user: Rep[String], override val password: Rep[String])
    extends AbsLogin(user, password)

  object LoginMethods {
    object toOper {
      def unapply(d: Def[_]): Option[Rep[Login]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[LoginElem] && method.getName == "toOper" =>
          Some(receiver).asInstanceOf[Option[Rep[Login]]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Login]] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  object LoginCompanionMethods {
  }

  def mkLogin
    (user: Rep[String], password: Rep[String]): Rep[Login] =
    new ExpLogin(user, password)
  def unmkLogin(p: Rep[Auth[SOption[String]]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: LoginElem @unchecked =>
      Some((p.asRep[Login].user, p.asRep[Login].password))
    case _ =>
      None
  }

  case class ExpHasPermission
      (override val user: Rep[String], override val password: Rep[String])
    extends AbsHasPermission(user, password)

  object HasPermissionMethods {
    object eA {
      def unapply(d: Def[_]): Option[Rep[HasPermission]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[HasPermissionElem] && method.getName == "eA" =>
          Some(receiver).asInstanceOf[Option[Rep[HasPermission]]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[HasPermission]] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }

    object toOper {
      def unapply(d: Def[_]): Option[Rep[HasPermission]] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[HasPermissionElem] && method.getName == "toOper" =>
          Some(receiver).asInstanceOf[Option[Rep[HasPermission]]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[HasPermission]] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  object HasPermissionCompanionMethods {
  }

  def mkHasPermission
    (user: Rep[String], password: Rep[String]): Rep[HasPermission] =
    new ExpHasPermission(user, password)
  def unmkHasPermission(p: Rep[Auth[Boolean]]) = p.elem.asInstanceOf[Elem[_]] match {
    case _: HasPermissionElem @unchecked =>
      Some((p.asRep[HasPermission].user, p.asRep[HasPermission].password))
    case _ =>
      None
  }

  object AuthMethods {
    object toOper {
      def unapply(d: Def[_]): Option[Rep[Auth[A]] forSome {type A}] = d match {
        case MethodCall(receiver, method, _, _) if receiver.elem.isInstanceOf[AuthElem[_, _]] && method.getName == "toOper" =>
          Some(receiver).asInstanceOf[Option[Rep[Auth[A]] forSome {type A}]]
        case _ => None
      }
      def unapply(exp: Exp[_]): Option[Rep[Auth[A]] forSome {type A}] = exp match {
        case Def(d) => unapply(d)
        case _ => None
      }
    }
  }

  object AuthCompanionMethods {
  }
}

object Authentications_Module extends scalan.ModuleInfo {
  val dump = "H4sIAAAAAAAAAL1WTWwbRRQe23Ecx0kbAoQfqSI15j/YFRXqIUJRmrhA5TpWtqXIVKDx7sTZMjsz7IyDzaFIHHoATghx41AJxKUXxBGpQgIkhBASCCEkzpxKUdUDPYF4M/vjtZtNwwF8GO3Ovnk/3/e957l8DeWljx6WNqaYVT2icNUyz6tSVaw6U64anOJOj5J1svXDE1/w6bfebWbRXBtNbmO5LmkbFYOHel/Ez5ZyGqiImU2k4r5U6HDDRKjZnFJiK5ezmut5PYU7lNQarlTLDTTR4c7gNXQBZRpozubM9oki1hrFUhIZ7k8RnZEbvxfN+2BDDGOwmq6ilqjitI9dBelDjLnAfpMIa8A4G3gKHQhT2xA6LbApkb6AGp73BDVhcg1UcD3BfRVFLUCEbe5ErxMMwwaab5zHO7gGUbs1S/ku62pnAtuv4i5pgok2n4AaJKFbpweChM5LUjkj8foCIQSsPGUSqw4xq8aYVTVmFYv4LqbuG1h/bPm8P0DBL5NDqC/AxdJtXEQeSJ05lbfP2S/dtEpeVh/u61QKJqFJcPRAikIMPYDtN5vvyRvPXjqWRdNtNO3K1Y5UPrZVUgYhXCXMGFcm5xhB7HeBwXIagybKKtiMyaRoc09gBp5CLGeAKOrartLGem8mpCcF+4ISJDLN9EUmrncxpV6jpTVMaevqfU8+9Hv9xSzKjoYogksLmsGPnCo0sdpT26FrvR5UqGAFeosDPpgWUJCW73og+h3y9Jefn7l+pZk3MecdsoV7VL2AaY8EegszGGajg2fLZYUmhwbF/vha2KPeGPlHrv7hfH0EncvGfIXl7U8i4CIvf/m59NNjK1k01TYddYLibhsok3VKvA1/jTPVRlN8h/jBl8IOpvppV8kUwvJDIpMM5IABhRZTx4Egmp5l02aZCIBS0ClNzkjlRKvyp/Xt+5d1I/hoJvgS8PW3e+yvXw9sKdMjwGxPEj/iNAdjJUBDL/cEAJuNQ3EkvQAfUwIweZ37zp5nRymaDvKwuEfuKN9wX770jjJkZPqjs2ejcx56fdmcO7wHL9FY/PTixbuvf/TKnaZ1pzqu8rCoHPkXjRv12X/YmCgGLxhJ9w/fDaJCz3HeddlaMm55/IBCeWM19rGUGW3O8X5dgHNbmEpQVeE455RgditBJkri0C2c/39a0euSWWv7AG7hOSxbxPdcKQG32wE4O2I9NEpUMBmGHgU1B1LbG2bYyqwmnKVgPFbR/gud1fF2qS+a0GYnHmeH0gcyCHNhs3EXvbZyJYvyJ0EcMKVkA+U7vMecSPFwhVGkr45He5lRxYPCsY+9WOHmt4iGaaWi0RomugSJVlMSXSc2xT5x9MWCeHDxCTr36AcrZ0/ee/aMmR0zjjEKvsQzc/dr2iksls2l4tE9LhVgVKl7Ai6N8HD0q2d+fPO7Tz42w3LIh0IHNdz6KmZHkWejIjjDjoxrK6fUZoXTAjR14eaHzce//+w386c4recOjHAW39WSf4ajWpsfSwLuYAm4QRN6JCUkZry4/wDEYSsSMgsAAA=="
}
}

