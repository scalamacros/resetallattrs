package scala.reflect.internal.resetallattrs

// NOTE: mostly copy/pasted from src/compiler/scala/tools/nsc/ast/Trees.scala
// NOTE: needs to live in package scala, because TypeTree.wasEmpty is private[scala]

class Helper(val universe: scala.reflect.internal.SymbolTable) {
  import universe._

  type UnaffiliatedTree = scala.reflect.api.Universe#Tree
  type AffiliatedTree = universe.Tree

  def impl(tree: UnaffiliatedTree): UnaffiliatedTree = {
    new ResetAttrs(brutally = true, leaveAlone = null).transform(tree.asInstanceOf[AffiliatedTree])
  }

  /** A transformer which resets symbol and tpe fields of all nodes in a given tree,
   *  with special treatment of:
   *    TypeTree nodes: are replaced by their original if it exists, otherwise tpe field is reset
   *                    to empty if it started out empty or refers to local symbols (which are erased).
   *    TypeApply nodes: are deleted if type arguments end up reverted to empty
   *    This(pkg) nodes where pkg is a package: these are kept.
   *
   *  (bq:) This transformer has mutable state and should be discarded after use
   */
  private class ResetAttrs(brutally: Boolean, leaveAlone: Tree => Boolean) {
    val locals = scala.collection.mutable.HashSet[Symbol]()
    def registerLocal(sym: Symbol): Unit = {
      if (sym != null && sym != NoSymbol) {
        locals += sym
      }
    }

    class MarkLocals extends universe.Traverser {
      def markLocal(tree: Tree): Unit = {
        if (tree.symbol != null && tree.symbol != NoSymbol) {
          val sym = tree.symbol
          registerLocal(sym)
          registerLocal(sym.sourceModule)
          registerLocal(sym.moduleClass)
          registerLocal(sym.companionClass)
          registerLocal(sym.companionModule)
          registerLocal(sym.deSkolemize)
          sym match {
            case sym: TermSymbol => registerLocal(sym.referenced)
            case _ => ;
          }
        }
      }

      override def traverse(tree: Tree) = {
        tree match {
         case _: DefTree | Function(_, _) | Template(_, _, _) =>
           markLocal(tree)
         case _ =>
           tree
        }

        super.traverse(tree)
      }
    }

    class Transformer extends universe.Transformer {
      override def transform(tree: Tree): Tree = {
        if (leaveAlone != null && leaveAlone(tree))
          tree
        else
          super.transform {
            tree match {
              case tree if !tree.canHaveAttrs =>
                tree
              case tpt: TypeTree =>
                if (tpt.original != null)
                  transform(tpt.original)
                else {
                  val refersToLocalSymbols = tpt.tpe != null && (tpt.tpe exists (tp => locals contains tp.typeSymbol))
                  val isInferred = tpt.wasEmpty
                  if (refersToLocalSymbols || isInferred) {
                    tpt.duplicate.clearType()
                  } else {
                    tpt
                  }
                }
              // If one of the type arguments of a TypeApply gets reset to an empty TypeTree, then this means that:
              // 1) It isn't empty now (tpt.tpe != null), but it was empty before (tpt.wasEmpty).
              // 2) Thus, its argument got inferred during a preceding typecheck.
              // 3) Thus, all its arguments were inferred (because scalac can only infer all or nothing).
              // Therefore, we can safely erase the TypeApply altogether and have it inferred once again in a subsequent typecheck.
              // UPD: Actually there's another reason for erasing a type behind the TypeTree
              // is when this type refers to symbols defined in the tree being processed.
              // These symbols will be erased, because we can't leave alive a type referring to them.
              // Here we can only hope that everything will work fine afterwards.
              case TypeApply(fn, args) if args map transform exists (_.isEmpty) =>
                transform(fn)
              case EmptyTree =>
                tree
              case _ =>
                val dupl = tree.duplicate
                // Typically the resetAttrs transformer cleans both symbols and types.
                // However there are exceptions when we cannot erase symbols due to idiosyncrasies of the typer.
                // vetoXXX local variables declared below describe the conditions under which we cannot erase symbols.
                //
                // The first reason to not erase symbols is the threat of non-idempotency (SI-5464).
                // Here we take care of references to package classes (SI-5705).
                // There are other non-idempotencies, but they are not worked around yet.
                //
                // The second reason has to do with the fact that resetAttrs needs to be less destructive.
                // Erasing locally-defined symbols is useful to prevent tree corruption, but erasing external bindings is not,
                // therefore we want to retain those bindings, especially given that restoring them can be impossible
                // if we move these trees into lexical contexts different from their original locations.
                if (dupl.hasSymbolField) {
                  val sym = dupl.symbol
                  val vetoScope = !brutally && !(locals contains sym) && !(locals contains sym.deSkolemize)
                  val vetoThis = dupl.isInstanceOf[This] && sym.isPackageClass
                  if (!(vetoScope || vetoThis)) dupl.symbol = NoSymbol
                }
                dupl.clearType()
            }
          }
      }
    }

    def transform(x: Tree): Tree = {
      new MarkLocals().traverse(x)
      new Transformer().transform(x)
    }
  }
}
