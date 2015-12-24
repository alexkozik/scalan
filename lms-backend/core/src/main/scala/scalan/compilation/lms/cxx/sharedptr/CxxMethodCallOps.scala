package scalan.compilation.lms.cxx.sharedptr

import scala.lms.common._
import scala.lms.internal.Effects
import scala.reflect.SourceContext

trait CxxMethodCallOps extends Base with Effects {
  // see http://en.cppreference.com/w/cpp/language/template_parameters
  sealed trait TemplateArg
  object NullPtrArg extends TemplateArg
  case class TypeArg(m: Manifest[_]) extends TemplateArg
  case class IntegralArg[A: Integral](value: A) extends TemplateArg
  case class PointerArg[A](target: Rep[A]) extends TemplateArg
  // LValueRefArg, MemberPointerArg, EnumerationArg not needed yet

  def cxxMethodCall[A: Manifest](caller: Rep[_], effects: Summary, methodName: String, templateArgs: List[TemplateArg], args: Rep[_]*): Rep[A]
}

trait CxxMethodCallOpsExp extends CxxMethodCallOps with BaseExp with EffectExp {

  case class CxxMethodCall[A: Manifest](caller: Rep[_], methodName: String, templateArgs: List[TemplateArg], args: List[Rep[_]]) extends Def[A] {
    val m = manifest[A]
  }

  def cxxMethodCall[A: Manifest](caller: Rep[_], effects: Summary, methodName: String, templateArgs: List[TemplateArg], args: Rep[_]*): Exp[A] = {
    reflectEffect(CxxMethodCall[A](caller, methodName, templateArgs, args.toList), effects)
  }

  override def mirror[A: Manifest](e: Def[A], f: Transformer)(implicit pos: SourceContext): Exp[A] = e match {
    case CxxMethodCall(caller, methodName, templateArgs, args) => CxxMethodCall[A](f(caller), methodName, templateArgs, args.map(f(_)))
    case Reflect(CxxMethodCall(caller, methodName, templateArgs, args), u, es) =>
      reflectMirrored(Reflect(CxxMethodCall[A](f(caller), methodName, templateArgs, args.map(f(_))), mapOver(f, u), f(es)))(mtype(manifest[A]), pos)
    case _ => super.mirror(e, f)
  }

  override def syms(e: Any): List[Sym[Any]] = e match {
    case CxxMethodCall(caller, methodName, templateArgs, args) if addControlDeps => syms(caller) ::: syms(args)
    case _ => super.syms(e)
  }

  override def boundSyms(e: Any): List[Sym[Any]] = e match {
    case CxxMethodCall(caller, methodName, templateArgs, args) => effectSyms(caller) ::: effectSyms(args)
    case _ => super.boundSyms(e)
  }

  override def symsFreq(e: Any): List[(Sym[Any], Double)] = e match {
    case CxxMethodCall(caller, methodName, templateArgs, args) => freqNormal(caller) ::: freqHot(args)
    case _ => super.symsFreq(e)
  }
}

trait CxxShptrGenMethodCallOps extends CxxShptrCodegen {
  val IR: CxxMethodCallOpsExp

  import IR._

  def quoteTemplateArg(x: TemplateArg) = x match {
    case NullPtrArg => "nullptr"
    case TypeArg(m) => remap(m)
    case IntegralArg(value) => value.toString
    // TODO need to make sure target is generated as a global or member. Do it in emitDataStructures?
    // But forward declaration is needed as well.
    case PointerArg(target) => src"&$target"
  }

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case CxxMethodCall(caller, methodName, templateArgs, args) =>
      val templateArgString =
        if (templateArgs.isEmpty) "" else s"<${templateArgs.map(quoteTemplateArg).mkString(",")}>"
      val argString = if (args.isEmpty) "" else src"($args)"
      val rhs1 = src"$caller->$methodName$templateArgString$argString"
      emitValDef(sym, rhs1)
    case _ => super.emitNode(sym, rhs)
  }
}