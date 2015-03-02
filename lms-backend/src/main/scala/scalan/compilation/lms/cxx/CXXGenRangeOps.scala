package scalan.compilation.lms.cxx

import scala.virtualization.lms.common.{BaseGenRangeOps, CLikeGenEffect}

trait CXXGenRangeOps extends CXXCodegen with CLikeGenEffect with BaseGenRangeOps {
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
//    case Until(start, end) => emitValDef(sym, src"$start until $end")

    case RangeForeach(start, end, i, body) => {
//      gen"""var $i : Int = $start
//          |val $sym = while ($i < $end) {
//          |${nestedBlock(body)}
//          |$i = $i + 1
//          |}"""

      emitConstruct(i, s"${quoteMove(start)}")
      gen"""while($i < $start) {
           |${nestedBlock(body)}
           |$i += 1;
           |}"""
    }

    case _ => super.emitNode(sym, rhs)
  }
}