package scalan.staged

import annotation.unchecked.uncheckedVariance
import scalan.{Base, ScalanStaged}
import scala.language.{implicitConversions}
import scalan.common.Lazy

trait BaseExp extends Base { self: ScalanStaged =>

  /**
   * constants/symbols (atomic)
   */
  abstract class Exp[+T] {
    def elem: Elem[T @uncheckedVariance]

    private[scalan] var isRec = false
    def isRecursive: Boolean = isRec
    private[scalan] def isRecursive_=(b: Boolean) = { isRec = b }

    def isVar: Boolean = this match {
      case Def(_) => false
      case _ => true
    }

    def isConst: Boolean = this match {
      case Def(Const(_)) => true
      case _ => false
    }
    def varName: String
    def toStringWithType = varName + ":" + elem.prettyName
    //def asSymbol = this.asInstanceOf[Sym[T]]
  }

  // this trait is mixed in Def[A]
  trait ReifiableObject[+T, +TImpl <: T] extends UserType[T @uncheckedVariance] {
    def name = getClass.getSimpleName
    def mirror(f: Transformer): Rep[_] = !!!("don't know how to mirror " + this)
    def decompose: Option[Rep[_]] = None
    def isScalarOp: Boolean = true
    def format: String = toString
  }

  type Def[+A] = ReifiableObject[A,A]
  //trait Def[+A] extends UserType[A @uncheckedVariance] with ReifiableObject[A]

  //type Def1[+A] = ReifiableObjectAux[A]

  case class Const[T](x: T)(implicit val leT: LElem[T]) extends Def[T] {
    def selfType = leT.value
    override def self: Rep[T] = this
    override def mirror(t: Transformer): Rep[_] = Const(x)
    override def hashCode: Int = (41 + x.hashCode)

    override def equals(other: Any) =
      other match {
        case c @ Const(otherX) => leT == c.leT && (otherX match {
          case otherArr: Array[_] => x match {
            case arr: Array[_] =>
              arr.sameElements(otherArr)
            case _ => false
          }
          case _ => otherX == x
        })
        case _ => false
      }

    override def toString = "Const(" + (x match {
      case arr: Array[_] => arr.mkString("Array(", ", ", ")")
      case _ => x
    }) + ")"
  }

  abstract class UnOp[T] extends Def[T] with UnOpBase[T,T] {
    override def mirror(t: Transformer) = {
      implicit val eT = arg.elem
      copyWith(t(arg))
    }
    override def self: Rep[T] = { implicit val e = selfType; this }
  }
  abstract class BinOp[T] extends Def[T] with BinOpBase[T,T] {
    override def mirror(t: Transformer) = {
      implicit val eT = lhs.elem
      copyWith(t(lhs), t(rhs))
    }
    override def self: Rep[T] = { implicit val e = selfType; this }
  }

  abstract class Transformer {
    def apply[A](x: Rep[A]): Rep[A]
    def isDefinedAt(x: Rep[_]): Boolean
    def domain: Set[Rep[_]]
    def apply[A](xs: Seq[Rep[A]]): Seq[Rep[A]] = xs map (e => apply(e))
    def apply[X,A](f: X=>Rep[A]): X=>Rep[A] = (z:X) => apply(f(z))
    def apply[X,Y,A](f: (X,Y)=>Rep[A]): (X,Y)=>Rep[A] = (z1:X,z2:Y) => apply(f(z1,z2))
  }

  trait TransformerOps[Ctx <: Transformer] {
    def empty: Ctx
    def add(ctx: Ctx, kv: (Rep[_], Rep[_])): Ctx
    def merge(ctx1: Ctx, ctx2: Ctx): Ctx = ctx2.domain.foldLeft(ctx1)((t,s) => add(t, (s, ctx2(s))))
  }

  implicit class TransformerEx[Ctx <: Transformer](self: Ctx)(implicit ops: TransformerOps[Ctx]) {
    def +(kv: (Rep[_], Rep[_])) = ops.add(self, kv)
    def ++(kvs: Map[Rep[_], Rep[_]]) = kvs.foldLeft(self)((ctx, kv) => ops.add(ctx,kv))
    def merge(other: Ctx): Ctx = ops.merge(self, other)
  }

  def fresh[T](implicit leT: LElem[T]): Exp[T]
  def findDefinition[T](s: Exp[T]): Option[TP[T]]
  def findDefinition[T](d: Def[T]): Option[TP[T]]
  def createDefinition[T](s: Exp[T], d: Def[T]): TP[T]

  /**
   * Updates the universe of symbols and definitions, then rewrites until fix-point
   * @param d A new graph node to add to the universe
   * @param newSym A symbol that will be used if d doesn't exist in the universe
   * @param et Type descriptor of the resulting type of node d
   * @tparam T
   * @return The symbol of the graph which is semantically(up to rewrites) equivalent to d
   */
  protected[scalan] def toExp[T](d: Def[T], newSym: => Exp[T])(implicit et: LElem[T]): Exp[T]
  implicit def reifyObject[T:LElem](obj: ReifiableObject[_,T]): Rep[T] = toExp(obj.asInstanceOf[Def[T]], fresh[T])

  override def toRep[A](x: A)(implicit eA: Elem[A]) = eA match {
    case `intElement` | `floatElement` | `boolElement` | `stringElement` => Const(x)
    case arrE: ArrayElem[a] => Const(x)
    case _ => super.toRep(x)(eA)
  }
  //protected[scalan] def toExp1[T](d: ReifiableObjectAux[T], newSym: => Exp[T])(implicit et: Elem[d.ThisType]): Exp[d.ThisType]
  //def reifyObject1[T](obj: ReifiableObjectAux[T])(implicit eT: Elem[obj.ThisType]): Rep[obj.ThisType] = toExp1(obj, fresh[obj.ThisType])

  object Def {
    def unapply[T](e: Exp[T]): Option[Def[T]] = findDefinition(e).map(_.rhs)
  }

  object Var {
    def unapply[T](e: Exp[T]): Option[Exp[T]] = e match {
      case Def(_) => None
      case _ => Some(e)
    }
  }
  object Exps {
    def unapply(xs: List[Any]): Option[List[Exp[_]]] = {
      val exps = xs map { a => a.asInstanceOf[Exp[_]] }
      Some(exps)
    }
  }
  object Elem {
    def unapply[T](s: Exp[T]): Option[(Exp[T],Elem[T])] = Some((s, s.elem))
  }


  trait TP[+T] {
    def sym: Exp[T]
    def definition: Option[Def[T]]
    def lambda: Option[Exp[_]]
    def rhs: Def[T] = definition.getOrElse(evaluate)
    def evaluate: Def[T] = !!!("invalid definition " + this, sym)
    def isLambda = rhs match { case l: Lambda[_,_] => true case _ => false }
  }

  trait TPCompanion {
    def apply[T](sym: Exp[T])(eval: => Def[T]): TP[T]
    def apply[T](sym: Exp[T], rhs: Def[T]): TP[T]
    def apply[T](sym: Exp[T], rhs: Def[T], lam: Exp[_]): TP[T]
    def unapply[T](tp: TP[T]): Option[(Exp[T], Def[T])]
  }

  val TP: TPCompanion = null

  object DefTP {
    def unapply[T](e: Exp[T]): Option[TP[T]] = findDefinition(e)
  }

  def decompose[T](d: Def[T]): Exp[_] = d.decompose match {
    case None => null
    case Some(sym) => sym.asInstanceOf[Exp[_]]
  }

  // dependencies
  def syms(e: Any): List[Exp[Any]] = e match {
    case s: Exp[_] => List(s)
    case p: Product => p.productIterator.toList.flatMap(syms(_))
    case _ => Nil
  }

  def dep(e: Exp[Any]): List[Exp[Any]] = e match {
    case Def(d: Product) => syms(d)
    case _ => Nil
  }
  def dep(e: Def[Any]): List[Exp[Any]] = e match {
    case d: Product => syms(d)
    case _ => Nil
  }

  //implicit def extendExpForSome(s: Exp[_]): ExpForSomeOps = new ExpForSomeOps(s)
  implicit class ExpForSomeOps(symbol: Exp[_]) {
    def inputs: List[Exp[Any]] = dep(symbol)
    def isLambda: Boolean = symbol match {
      case Def(_: Lambda[_, _]) => true
      case _ => false
    }
    def tp: TP[_] = findDefinition(symbol).get
    def sameScopeAs(other: Exp[_]): Boolean = this.tp.lambda == other.tp.lambda

    def asPair[A,B,R](f: Elem[A] => Elem[B] => Rep[(A,B)] => R): R = {
      val elem = symbol.elem
      elem match {
        case _: PairElem[_,_] => {
          val pe = elem.asInstanceOf[PairElem[A,B]]
          f(pe.ea)(pe.eb)(symbol.asRep[(A,B)])
        }
        case _ => !!!(s"Symbol $symbol expected to have PairElem but it's ${elem.name}")
      }
    }

    def asFunc[A,B,R](f: Elem[A] => Elem[B] => Rep[A=>B] => R): R = {
      val elem = symbol.elem
      elem match {
        case _: FuncElem[_,_] => {
          val fe = elem.asInstanceOf[FuncElem[A,B]]
          f(fe.ea)(fe.eb)(symbol.asRep[A=>B])
        }
        case _ => !!!(s"Symbol $symbol expected to have FuncElem but it's ${elem.name}")
      }
    }

    def mirror(t: Transformer) = symbol match {
      case Def(d) => d.mirror(t)
      case _ => fresh(Lazy(symbol.elem.asInstanceOf[Elem[Any]]))
    }
  }

  implicit class DefForSomeOps(d: Def[_]) {
    def getDeps: List[Exp[_]] = d match {
      case lam: Lambda[_,_] => lam.freeVars.toList
      case _ => syms(d)
    }
  }
  //implicit def extendDefForSome(d: Def[_]) = new DefForSomeOps(d)

  def rewrite[T](d: Def[T])(implicit eT: LElem[T]): Exp[_] = {
    rewriteRules.foreach(r =>
      r.lift(d) match {
        case Some(e) => return e
        case _ =>
      })
    null
  }

  var rewriteRules = List[PartialFunction[Def[_], Exp[_]]]()

  def addRewriteRules(rules: PartialFunction[Def[_], Exp[_]]*) {
    rewriteRules ++= rules
  }

  trait RewriteRule[A] {
    def unapply(d: Exp[_]): Option[A]
    def apply(x: A): Exp[_]
  }
  implicit def rewriteRuleToPartialFunction[A](rule: RewriteRule[A]) = new PartialFunction[Exp[_], Exp[_]] {
    def isDefinedAt(s: Exp[_]) = rule.unapply(s).isDefined
    def apply(s: Exp[_]) = rule.unapply(s) match {
      case Some(args) => rule(args)
      case None =>
        println("rewriting error in %s".format(s))
        s
    }
  }

}

/**
 * The Expressions trait houses common AST nodes. It also manages a list of encountered Definitions which
 * allows for common sub-expression elimination (CSE).
 *
 * @since 0.1
 */
trait Expressions extends BaseExp { self: ScalanStaged =>
  /**
   * A Sym is a symbolic reference used internally to refer to expressions.
   */
  object Sym { private var currId = 0 }
  case class Sym[+T](id: Int = {Sym.currId += 1; Sym.currId})
                    (implicit et: LElem[T]) extends Exp[T]
  {
    override def elem: Elem[T @uncheckedVariance] = this match {
      case Def(d) => d.selfType.asInstanceOf[Elem[T]]
      case _ => et.value
    }
    def varName = "s" + id
    override def toString = {
      val res = isDebug match {
        case false => varName
        case _ =>
          val rhs = findDefinition(this) match { case Some(TP(_, d)) => "->" + d.toString case _ => "" }
          "s" + id + rhs
      }
      res
    }

    lazy val definition = findDefinition(this).map(_.rhs)
  }

  def fresh[T](implicit et: LElem[T]): Exp[T] = new Sym[T]()

  class TPS[T](val sym: Exp[T], val definition: Option[Def[T]], val lambda: Option[Exp[_]]) extends TP[T] {
  }

  override val TP = new TPCompanion {
    def apply[T](sym: Exp[T])(eval: => Def[T]) = new TPS(sym, None, None) { override def evaluate = eval }
    def apply[T](sym: Exp[T], rhs: Def[T]) = new TPS(sym, Some(rhs), None)
    def apply[T](sym: Exp[T], rhs: Def[T], lam: Exp[_]) = new TPS(sym, Some(rhs), Some(lam))
    def unapply[T](tp: TP[T]): Option[(Exp[T], Def[T])] = Some((tp.sym, tp.rhs))
    //def unapply[T](s: Exp[T]): Option[TP[T]] = findDefinition(s)
  }

  private[this] var expToGlobalDefs: Map[Exp[_], TP[_]] = Map.empty

  def findDefinition[T](s: Exp[T]): Option[TP[T]] =
    expToGlobalDefs.get(s).asInstanceOf[Option[TP[T]]]

  def findDefinition[T](d: Def[T]): Option[TP[T]] =
    expToGlobalDefs.valuesIterator.find(_.rhs == d).asInstanceOf[Option[TP[T]]]

  def findOrCreateDefinition[T](d: Def[T], newSym: => Exp[T]): Exp[T] = {
    val res = findDefinition(d) match {
      case Some(TP(s, _)) => s
      case None =>
        val TP(s, _) = createDefinition(newSym, d)
        s
    }
    res
  }

  def createDefinition[T](s: Exp[T], d: Def[T]): TP[T] = {
    val tp = lambdaStack.top match {
      case Some(fSym) => TP(s, d, fSym)
      case _ => TP(s, d)
    }
    expToGlobalDefs += tp.sym -> tp
    tp
  }

  /**
   * Updates the universe of symbols and definitions, then rewrites until fix-point
   * @param d A new graph node to add to the universe
   * @param newSym A symbol that will be used if d doesn't exist in the universe
   * @param et Type descriptor of the resulting type of node d
   * @tparam T
   * @return The symbol of the graph which is semantically(up to rewrites) equivalent to d
   */
  protected[scalan] def toExp[T](d: Def[T], newSym: => Exp[T])(implicit et: LElem[T]): Exp[T] = {
    var res = findOrCreateDefinition(d, newSym)
    var currSym = res
    var currDef = d
    do {
      currSym = res
      val ns = rewrite(currDef).asRep[T]
      ns match {
        case null => {}
        case Var(_) => {
          res = ns; currDef = null
        }
        case Def(someOtherD) => {
          res = ns
          currDef = someOtherD
        }
      }
    } while (res != currSym && currDef != null)
    res
  }

}
