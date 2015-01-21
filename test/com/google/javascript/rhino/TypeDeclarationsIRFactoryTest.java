package com.google.javascript.rhino;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import junit.framework.TestCase;

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.Node.NULLABLE_TYPE;
import static com.google.javascript.rhino.Token.BOOLEAN_TYPE;
import static com.google.javascript.rhino.Token.FUNCTION_TYPE;
import static com.google.javascript.rhino.Token.NULL_TYPE;
import static com.google.javascript.rhino.Token.NUMBER_TYPE;
import static com.google.javascript.rhino.Token.PARAMETERIZED_TYPE;
import static com.google.javascript.rhino.Token.REST_PARAMETER_TYPE;
import static com.google.javascript.rhino.Token.STRING_TYPE;
import static com.google.javascript.rhino.Token.UNION_TYPE;
import static com.google.javascript.rhino.Token.UNKNOWN_TYPE;
import static com.google.javascript.rhino.Token.VOID_TYPE;

public class TypeDeclarationsIRFactoryTest extends TestCase {

  public void testSimpleTypes() {
    assertParseTypeAndConvert("*").hasType(Token.ANY_TYPE);
    assertParseTypeAndConvert("boolean").hasType(BOOLEAN_TYPE);
    assertParseTypeAndConvert("null").hasType(NULL_TYPE);
    assertParseTypeAndConvert("number").hasType(NUMBER_TYPE);
    assertParseTypeAndConvert("string").hasType(STRING_TYPE);
    assertParseTypeAndConvert("void").hasType(VOID_TYPE);
    assertParseTypeAndConvert("undefined").hasType(UNKNOWN_TYPE);
  }

  public void testNamedTypes() throws Exception {
    assertParseTypeAndConvert("Window")
        .isEqualTo(IR.name("Window"));
    assertParseTypeAndConvert("goog.ui.Menu")
        .isEqualTo(IR.getprop(IR.getprop(IR.name("goog"), IR.string("ui")), IR.string("Menu")));
  }

  public void testTypeApplication() throws Exception {
    assertParseTypeAndConvert("Array.<string>")
        .isEqualTo(new Node(PARAMETERIZED_TYPE, IR.name("Array"), new Node(STRING_TYPE)));
    assertParseTypeAndConvert("Object.<string, number>")
        .isEqualTo(new Node(PARAMETERIZED_TYPE, IR.name("Object"),
            new Node(STRING_TYPE), new Node(NUMBER_TYPE)));
  }

  public void testTypeUnion() throws Exception {
    assertParseTypeAndConvert("(number|boolean)")
        .isEqualTo(new Node(UNION_TYPE,
            new Node(NUMBER_TYPE), new Node(BOOLEAN_TYPE)));
  }

  public void testRecordType() throws Exception {
    Node key1 = IR.stringKey("myNum");
    key1.addChildToFront(new Node(NUMBER_TYPE));
    Node key2 = IR.stringKey("myObject");
    key2.addChildToFront(new Node(UNKNOWN_TYPE));
    assertParseTypeAndConvert("{myNum: number, myObject}")
        .isEqualTo(IR.objectlit(key1, key2));
  }

  public void testRecordTypeWithTypeApplication() throws Exception {
    Node key = IR.stringKey("length");
    key.addChildToFront(new Node(UNKNOWN_TYPE));
    assertParseTypeAndConvert("Array.<{length}>")
        .isEqualTo(new Node(PARAMETERIZED_TYPE, IR.name("Array"), IR.objectlit(key)));
  }

  public void testNullableType() throws Exception {
    assertParseTypeAndConvert("?number")
        .isEqualTo(new Node(UNION_TYPE, new Node(NULL_TYPE), new Node(NUMBER_TYPE)));
  }

  public void testNonNullableType() throws Exception {
    assertParseTypeAndConvert("!Object")
        .isEqualTo(IR.name("Object"));
    assertParseTypeAndConvert("!Object")
        .hasBooleanProperty(NULLABLE_TYPE, false);
  }

  public void testFunctionType() throws Exception {
    Node stringKey = IR.stringKey("p1");
    stringKey.addChildToFront(new Node(STRING_TYPE));
    Node stringKey1 = IR.stringKey("p2");
    stringKey1.addChildToFront(new Node(BOOLEAN_TYPE));
    assertParseTypeAndConvert("function(string, boolean)")
        .isEqualTo(new Node(FUNCTION_TYPE, new Node(UNKNOWN_TYPE), stringKey, stringKey1));
  }

  public void testFunctionReturnType() throws Exception {
    assertParseTypeAndConvert("function(): number")
        .isEqualTo(new Node(FUNCTION_TYPE, new Node(NUMBER_TYPE)));
  }

  public void testFunctionThisType() throws Exception {
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(new Node(STRING_TYPE));
    Node expected = new Node(FUNCTION_TYPE, new Node(UNKNOWN_TYPE), stringKey1);
    expected.putProp(Node.FUNCTION_THIS_TYPE, IR.getprop(IR.getprop(IR.name("goog"), IR.string("ui")), IR.string("Menu")));
    assertParseTypeAndConvert("function(this:goog.ui.Menu, string)")
        .isEqualTo(expected);
  }

  public void testFunctionNewType() throws Exception {
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(new Node(STRING_TYPE));
    assertParseTypeAndConvert("function(new:goog.ui.Menu, string)")
        .isEqualTo(new Node(FUNCTION_TYPE, new Node(UNKNOWN_TYPE), stringKey1));
  }

  public void testVariableParameters() throws Exception {
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(new Node(STRING_TYPE));
    Node stringKey2 = IR.stringKey("p2");
    stringKey2.addChildToFront(new Node(REST_PARAMETER_TYPE, new Node(NUMBER_TYPE)));
    assertParseTypeAndConvert("function(string, ...number): number")
        .isEqualTo(new Node(FUNCTION_TYPE, new Node(NUMBER_TYPE), stringKey1, stringKey2));
  }

  private NodeSubject assertParseTypeAndConvert(final String typeExpr) {
    Node oldAST = JsDocInfoParser.parseTypeString(typeExpr);
    if (oldAST == null) {
      fail(typeExpr + " did not produce a parsed AST");
    }
    return new NodeSubject(THROW_ASSERTION_ERROR, TypeDeclarationsIRFactory.convertTypeNodeAST(oldAST));
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

    public void hasBooleanProperty(int property, boolean value) {
      assertThat(getSubject().getBooleanProp(property)).isEqualTo(value);
    }
  }
}