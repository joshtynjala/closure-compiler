package com.google.javascript.jscomp.parsing;

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.anyType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.booleanType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.namedType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.nullType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.numberType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.optionalParameter;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.parameterizedType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.recordType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.stringType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.undefinedType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.unionType;
import static com.google.javascript.rhino.Node.TypeDeclarationNode;
import static com.google.javascript.rhino.Token.ANY_TYPE;
import static com.google.javascript.rhino.Token.BOOLEAN_TYPE;
import static com.google.javascript.rhino.Token.FUNCTION_TYPE;
import static com.google.javascript.rhino.Token.NAMED_TYPE;
import static com.google.javascript.rhino.Token.NULL_TYPE;
import static com.google.javascript.rhino.Token.NUMBER_TYPE;
import static com.google.javascript.rhino.Token.PARAMETERIZED_TYPE;
import static com.google.javascript.rhino.Token.RECORD_TYPE;
import static com.google.javascript.rhino.Token.REST_PARAMETER_TYPE;
import static com.google.javascript.rhino.Token.STRING_TYPE;
import static com.google.javascript.rhino.Token.UNDEFINED_TYPE;
import static com.google.javascript.rhino.Token.VOID_TYPE;
import static java.util.Arrays.asList;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.util.LinkedHashMap;

public class TypeDeclarationsIRFactoryTest extends TestCase {

  public void testConvertSimpleTypes() {
    assertParseTypeAndConvert("?").hasType(ANY_TYPE);
    assertParseTypeAndConvert("boolean").hasType(BOOLEAN_TYPE);
    assertParseTypeAndConvert("null").hasType(NULL_TYPE);
    assertParseTypeAndConvert("number").hasType(NUMBER_TYPE);
    assertParseTypeAndConvert("string").hasType(STRING_TYPE);
    assertParseTypeAndConvert("void").hasType(VOID_TYPE);
    assertParseTypeAndConvert("undefined").hasType(UNDEFINED_TYPE);
  }

  public void testConvertStarType() throws Exception {
    assertParseTypeAndConvert("*").isEqualTo(unionType(
        namedType("Object"), numberType(), stringType(),
        booleanType(), nullType(), undefinedType()));
  }

  public void testConvertNamedTypes() throws Exception {
    assertParseTypeAndConvert("Window")
        .isEqualTo(namedType("Window"));
    assertParseTypeAndConvert("goog.ui.Menu")
        .isEqualTo(namedType("goog.ui.Menu"));

    assertNode(namedType("goog.ui.Menu"))
        .isEqualTo(new TypeDeclarationNode(NAMED_TYPE,
            IR.getprop(IR.getprop(IR.name("goog"), IR.string("ui")), IR.string("Menu"))));
  }

  public void testConvertTypeApplication() throws Exception {
    assertParseTypeAndConvert("Array.<string>")
        .isEqualTo(parameterizedType(namedType("Array"), asList(stringType())));
    assertParseTypeAndConvert("Object.<string, number>")
        .isEqualTo(parameterizedType(namedType("Object"), asList(stringType(), numberType())));

    assertNode(parameterizedType(namedType("Array"), asList(stringType())))
        .isEqualTo(new TypeDeclarationNode(PARAMETERIZED_TYPE,
            new TypeDeclarationNode(NAMED_TYPE, IR.name("Array")),
            new TypeDeclarationNode(STRING_TYPE)));
  }

  public void testConvertTypeUnion() throws Exception {
    assertParseTypeAndConvert("(number|boolean)")
        .isEqualTo(unionType(numberType(), booleanType()));
  }

  public void testConvertRecordType() throws Exception {
    LinkedHashMap<String, TypeDeclarationNode> properties = new LinkedHashMap<>();
    properties.put("myNum", numberType());
    properties.put("myObject", anyType());

    assertParseTypeAndConvert("{myNum: number, myObject}")
        .isEqualTo(recordType(properties));
  }

  public void testCreateRecordType() throws Exception {
    LinkedHashMap<String, TypeDeclarationNode> properties = new LinkedHashMap<>();
    properties.put("myNum", numberType());
    properties.put("myObject", anyType());
    TypeDeclarationNode node = recordType(properties);

    Node key1 = IR.stringKey("myNum");
    key1.addChildToFront(new TypeDeclarationNode(NUMBER_TYPE));
    Node key2 = IR.stringKey("myObject");
    key2.addChildToFront(new TypeDeclarationNode(ANY_TYPE));

    assertNode(node)
        .isEqualTo(new TypeDeclarationNode(RECORD_TYPE, key1, key2));
  }

  public void testConvertRecordTypeWithTypeApplication() throws Exception {
    Node key = IR.stringKey("length");
    key.addChildToFront(anyType());
    assertParseTypeAndConvert("Array.<{length}>")
        .isEqualTo(new TypeDeclarationNode(PARAMETERIZED_TYPE,
            namedType("Array"),
            new TypeDeclarationNode(RECORD_TYPE, key)));
  }

  public void testConvertNullableType() throws Exception {
    assertParseTypeAndConvert("?number")
        .isEqualTo(unionType(nullType(), numberType()));
  }

  // TODO(alexeagle): change this test once we can capture nullability constraints in TypeScript
  public void testConvertNonNullableType() throws Exception {
    assertParseTypeAndConvert("!Object")
        .isEqualTo(namedType("Object"));
  }

  public void testConvertFunctionType() throws Exception {
    Node stringKey = IR.stringKey("p1");
    stringKey.addChildToFront(stringType());
    Node stringKey1 = IR.stringKey("p2");
    stringKey1.addChildToFront(booleanType());
    assertParseTypeAndConvert("function(string, boolean)")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, anyType(), stringKey, stringKey1));
  }

  public void testConvertFunctionReturnType() throws Exception {
    assertParseTypeAndConvert("function(): number")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, numberType()));
  }

  public void testConvertFunctionThisType() throws Exception {
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(stringType());
    assertParseTypeAndConvert("function(this:goog.ui.Menu, string)")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, anyType(), stringKey1));
  }

  public void testConvertFunctionNewType() throws Exception {
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(stringType());
    assertParseTypeAndConvert("function(new:goog.ui.Menu, string)")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, anyType(), stringKey1));
  }

  public void testConvertVariableParameters() throws Exception {
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(stringType());
    Node stringKey2 = IR.stringKey("p2");
    stringKey2.addChildToFront(new TypeDeclarationNode(REST_PARAMETER_TYPE, numberType()));
    assertParseTypeAndConvert("function(string, ...number): number")
        .isEqualTo(new TypeDeclarationNode(FUNCTION_TYPE, numberType(), stringKey1, stringKey2));
  }

  public void testConvertOptionalFunctionParameters() throws Exception {
    LinkedHashMap<String, TypeDeclarationNode> parameters = new LinkedHashMap<>();
    parameters.put("p1", optionalParameter(unionType(nullType(), stringType())));
    parameters.put("p2", optionalParameter(numberType()));
    assertParseTypeAndConvert("function(?string=, number=)")
        .isEqualTo(TypeDeclarationsIRFactory.functionType(anyType(), parameters));
  }

  private NodeSubject assertNode(final Node node) {
    return new NodeSubject(THROW_ASSERTION_ERROR, node);
  }

  private NodeSubject assertParseTypeAndConvert(final String typeExpr) {
    Node oldAST = JsDocInfoParser.parseTypeString(typeExpr);
    if (oldAST == null) {
      fail(typeExpr + " did not produce a parsed AST");
    }
    return new NodeSubject(THROW_ASSERTION_ERROR,
        TypeDeclarationsIRFactory.convertTypeNodeAST(oldAST));
  }

  private class NodeSubject extends Subject<NodeSubject, Node> {
    public NodeSubject(FailureStrategy failureStrategy, Node subject) {
      super(failureStrategy, subject);
    }

    public void isEqualTo(Node node) {
      String treeDiff = node.checkTreeEquals(getSubject());
      if (treeDiff != null) {
        failWithRawMessage("%s", treeDiff);
      }
    }

    public void hasType(int tokenType) {
      assertThat(getSubject().getType()).is(tokenType);
    }
  }
}