package com.google.javascript.rhino;

import com.google.common.base.Preconditions;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import junit.framework.TestCase;

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.Token.*;
import static com.google.javascript.rhino.Token.PARAMETERIZED_TYPE;
import static com.google.javascript.rhino.Token.STRING;

public class JSTypeTreesTest extends TestCase {

  public void testSimpleTypes() {
    assertParseTypeAndConvert("*").hasType(STAR);
    assertParseTypeAndConvert("boolean").hasType(Token.BOOLEAN_TYPE);
    assertParseTypeAndConvert("null").hasType(NULL);
    assertParseTypeAndConvert("number").hasType(NUMBER_TYPE);
    assertParseTypeAndConvert("string").hasType(STRING);
    assertParseTypeAndConvert("void").hasType(VOID);
    assertParseTypeAndConvert("undefined").hasType(VOID);
  }

  public void testNamedTypes() throws Exception {
    assertParseTypeAndConvert("Window")
        .isEqualTo(IR.name("Window"));
    assertParseTypeAndConvert("goog.ui.Menu")
        .isEqualTo(IR.getprop(IR.getprop(IR.name("goog"), IR.string("ui")), IR.string("Menu")));
  }

  public void testTypeApplication() throws Exception {
    assertParseTypeAndConvert("Array.<string>")
        .isEqualTo(new Node(PARAMETERIZED_TYPE, IR.name("Array"), new Node(STRING)));
    assertParseTypeAndConvert("Object.<string, number>")
        .isEqualTo(new Node(PARAMETERIZED_TYPE, IR.name("Object"),
            new Node(STRING), new Node(Token.NUMBER_TYPE)));
  }

  public void testTypeUnion() throws Exception {
    assertParseTypeAndConvert("(number|boolean)")
        .isEqualTo(new Node(Token.UNION_TYPE,
            new Node(Token.NUMBER_TYPE), new Node(Token.BOOLEAN_TYPE)));
  }

  public void testRecordType() throws Exception {
    Node key1 = IR.stringKey("myNum");
    key1.addChildToFront(new Node(NUMBER_TYPE));
    Node key2 = IR.stringKey("myObject");
    key2.addChildToFront(new Node(QMARK));
    assertParseTypeAndConvert("{myNum: number, myObject}")
        .isEqualTo(IR.objectlit(key1, key2));
  }

  public void testRecordTypeWithTypeApplication() throws Exception {
    Node key = IR.stringKey("length");
    key.addChildToFront(new Node(QMARK));
    assertParseTypeAndConvert("Array.<{length}>")
        .isEqualTo(new Node(PARAMETERIZED_TYPE, IR.name("Array"), IR.objectlit(key)));
  }

  public void testNullableType() throws Exception {
    assertParseTypeAndConvert("?number")
        .isEqualTo(new Node(Token.UNION_TYPE, new Node(Token.NULL), new Node(Token.NUMBER_TYPE)));
  }

  public void testNonNullableType() throws Exception {
    assertParseTypeAndConvert("!Object")
        .isEqualTo(new Node(Token.BANG, IR.name("Object")));
  }

  public void testFunctionType() throws Exception {
    Node stringKey = IR.stringKey("p1");
    stringKey.addChildToFront(new Node(Token.STRING));
    Node stringKey1 = IR.stringKey("p2");
    stringKey1.addChildToFront(new Node(Token.BOOLEAN_TYPE));
    assertParseTypeAndConvert("function(string, boolean)")
        .isEqualTo(new Node(FUNCTION_TYPE, new Node(EMPTY), stringKey, stringKey1));
  }

  public void testFunctionReturnType() throws Exception {
    assertParseTypeAndConvert("function(): number")
        .isEqualTo(new Node(FUNCTION_TYPE, new Node(NUMBER_TYPE)));
  }

  public void testFunctionThisType() throws Exception {
    // FIXME: this is pretty sketchy!
    Node stringKey = IR.stringKey("this");
    stringKey.addChildToFront(IR.getprop(IR.getprop(IR.name("goog"), IR.string("ui")), IR.string("Menu")));
    Node stringKey1 = IR.stringKey("p1");
    stringKey1.addChildToFront(new Node(Token.STRING));
    assertParseTypeAndConvert("function(this:goog.ui.Menu, string)")
        .isEqualTo(new Node(FUNCTION_TYPE, new Node(EMPTY), stringKey, stringKey1));
  }

  public void testFunctionNewType() throws Exception {

  }

  private NodeSubject assertParseTypeAndConvert(final String typeComment) {
    Node oldAST = JsDocInfoParser.parseTypeString(typeComment);
    if (oldAST == null) {
      fail(typeComment + " did not produce a parsed AST");
    }
    return new NodeSubject(THROW_ASSERTION_ERROR, JSTypeTrees.convertTypeNodeAST(oldAST));
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