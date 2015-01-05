package com.google.javascript.jscomp.parsing.parser.trees;

import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

/**
 * A parameter with a type specified.
 */
public class TypedParameterTree extends ParseTree {

  public final ParseTree inner;
  public final ParseTree typeAnnotation;

  public TypedParameterTree(SourceRange location, ParseTree inner, ParseTree typeAnnotation) {
    super(ParseTreeType.TYPE_ANNOTATION, location);
    this.inner = inner;
    this.typeAnnotation = typeAnnotation;
  }
}
