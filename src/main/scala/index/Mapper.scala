package org.scalex
package index

import model._
import scala.tools.nsc.doc.base.{ comment ⇒ nscComment }
import scala.tools.nsc.doc.{ model ⇒ nsc }

private[index] final class Mapper {

  var seen = scala.collection.mutable.Set[nsc.DocTemplateEntity]()

  def docTemplate(o: nsc.DocTemplateEntity): DocTemplate = {
    seen += o
    DocTemplate(
      memberTemplate = memberTemplate(o),
      // inSource = o.inSource map { case (file, line) ⇒ (file.path, line) },
      sourceUrl = o.sourceUrl map (_.toString),
      members = filter(o.members) map member,
      templates = filter(o.templates) collect {
        case t: nsc.DocTemplateEntity ⇒ docTemplate(t)
      },
      methods = filter(o.methods) map method,
      values = filter(o.values) map value,
      abstractTypes = filter(o.abstractTypes) map abstractType,
      aliasTypes = filter(o.aliasTypes) map aliasType,
      primaryConstructor = o.primaryConstructor map constructor,
      constructors = filter(o.constructors) map constructor,
      companion = filter(o.companion) map docTemplate,
      conversions = o.conversions map implicitConversion,
      outgoingImplicitlyConvertedClasses = o.outgoingImplicitlyConvertedClasses map {
        case (tpl, typ, imp) ⇒ (template(tpl), typeEntity(typ), implicitConversion(imp))
      }
    )
  }

  def constructor(o: nsc.Constructor) = Constructor(
    member = member(o),
    valueParams = o.valueParams map2 valueParam)

  def aliasType(o: nsc.AliasType) = AliasType(
    alias = typeEntity(o.alias))

  def abstractType(o: nsc.AbstractType) = AbstractType(
    member = member(o),
    higherKinded = higherKinded(o),
    lo = o.lo map (_.name),
    hi = o.hi map (_.name))

  def value(o: nsc.Val) = Val(member = member(o))

  def method(o: nsc.Def) = Def(
    member = member(o),
    higherKinded = higherKinded(o),
    valueParams = o.valueParams map2 valueParam)

  def memberTemplate(o: nsc.MemberTemplateEntity) = MemberTemplate(
    template = template(o),
    member = member(o),
    higherKinded = higherKinded(o),
    valueParams = o.valueParams map (_ map valueParam),
    parentTypes = o.parentTypes map {
      case (tpl, typ) ⇒ TemplateAndType(template(tpl), typeEntity(typ))
    })

  def template(o: nsc.TemplateEntity) = Template(
    entity = entity(o),
    role = if (o.isPackage) Role.Package
      else if (o.isObject) Role.Object
      else if (o.isTrait) Role.Trait
      else if (o.isCaseClass) Role.CaseClass
      else if (o.isClass) Role.Class
      else Role.Unknown,
    isDocTemplate = o.isDocTemplate,
    selfType = o.selfType map typeEntity)

  def member(o: nsc.MemberEntity): Member = Member(
    entity = entity(o),
    // comment = o.comment map comment,
    inDefinitionTemplates = o.inDefinitionTemplates map (_.qualifiedName),
    flags = (o.flags collect {
      case nscComment.Paragraph(nscComment.Text(flag)) ⇒ flag
    }) ::: List(
      o.deprecation.isDefined option "deprecated",
      o.migration.isDefined option "migration"
    ).flatten,
    resultType = typeEntity(o.resultType),
    role = if (o.isDef) Role.Def
      else if (o.isVal) Role.Val
      else if (o.isLazyVal) Role.LazyVal
      else if (o.isVar) Role.Var
      else if (o.isConstructor) Role.Constructor
      else if (o.isAliasType) Role.AliasType
      else if (o.isAbstractType) Role.AbstractType
      else Role.Unknown,
    byConversion = o.byConversion map implicitConversion,
    isImplicitlyInherited = o.isImplicitlyInherited)

  def entity(o: nsc.Entity) = Entity(
    name = o.name,
    qualifiedName = o.qualifiedName)

  def typeEntity(o: nsc.TypeEntity) = o.name

  def comment(o: nscComment.Comment) = Comment(body = o.body)

  def implicitConversion(o: nsc.ImplicitConversion) = ImplicitConversion(
    source = docTemplate(o.source),
    targetType = typeEntity(o.targetType),
    targetTypeComponents = o.targetTypeComponents map {
      case (tpl, typ) ⇒ TemplateAndType(template(tpl), typeEntity(typ))
    },
    convertorMethod = o.convertorMethod.left map member,
    conversionShortName = o.conversionShortName,
    conversionQualifiedName = o.conversionShortName,
    convertorOwner = template(o.convertorOwner),
    members = o.members map member,
    isHiddenConversion = o.isHiddenConversion)

  def higherKinded(o: nsc.HigherKinded): HigherKinded = HigherKinded(
    typeParams = o.typeParams map typeParam
  )

  def typeParam(o: nsc.TypeParam): TypeParam = TypeParam(
    name = o.name,
    higherKinded = higherKinded(o),
    variance = o.variance,
    lo = o.lo map typeEntity,
    hi = o.lo map typeEntity)

  def valueParam(o: nsc.ValueParam) = ValueParam(
    name = o.name,
    resultType = typeEntity(o.resultType),
    defaultValue = o.defaultValue map (_.expression),
    isImplicit = o.isImplicit)

  def filter[M[_]: scalaz.MonadPlus, A <: nsc.Entity](entities: M[A]): M[A] =
    implicitly[scalaz.MonadPlus[M]].filter(entities) {
      case t: nsc.DocTemplateEntity ⇒ !seen(t)
      case t: nsc.MemberEntity ⇒
        !(t.inDefinitionTemplates exists { tpl ⇒
          ignoredTemplates contains tpl.qualifiedName
        }) && !t.isShadowedOrAmbiguousImplicit
      case _ ⇒ false
    }

  private def ignoredTemplates = Set(
    "scala.Any",
    "scala.AnyRef")
}