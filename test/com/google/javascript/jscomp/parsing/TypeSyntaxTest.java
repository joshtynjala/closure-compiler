/*
 * Copyright 2015 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.parsing;

import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.SimpleSourceFile;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.TestErrorReporter;

public class TypeSyntaxTest extends BaseJSTypeTestCase {

  private TestErrorReporter testErrorReporter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    testErrorReporter = new TestErrorReporter(null, null);
  }

  private void expectErrors(String... errors) {
    testErrorReporter.setErrors(errors);
  }

  private void expectWarnings(String... warnings) {
    testErrorReporter.setWarnings(warnings);
  }

  public void testVariableDeclaration() {
    JSTypeExpression type = parseTypeOfVar("var foo: string = 'hello';");
    assertTypeEquals(STRING_TYPE, type);
  }

  public void testVariableDeclaration_errorIncomplete() {
    expectErrors("Unexpected end of type expression");
    parse("var foo: = 'hello';");
  }

  public void testTypeInDocAndSyntax() {
    expectErrors("Bad type annotation - can only have JSDoc or inline type annotations, not both");
    parse("var /** string */ foo: string = 'hello';");
  }

  public void testFunctionParamDeclaration() {
    Node fn = parse("function foo(x: string) {}").getFirstChild();
    JSDocInfo paramInfo = fn.getFirstChild().getNext().getFirstChild().getJSDocInfo();
    assertTypeEquals(STRING_TYPE, paramInfo.getType());
  }

  public void testFunctionParamDeclaration_defaultValue() {
    Node fn = parse("function foo(x: string = 'hello') {}").getFirstChild();
    JSDocInfo paramInfo = fn.getFirstChild().getNext().getFirstChild().getJSDocInfo();
    assertTypeEquals(STRING_TYPE, paramInfo.getType());
  }

  public void testFunctionParamDeclaration_destructuringArray() {
    // TODO(martinprobst): implement.
    expectErrors("',' expected");
    parse("function foo([x]: string) {}");
  }

  public void testFunctionParamDeclaration_destructuringArrayInner() {
    // TODO(martinprobst): implement.
    expectErrors("']' expected");
    parse("function foo([x: string]) {}");
  }

  public void testFunctionParamDeclaration_destructuringObject() {
    // TODO(martinprobst): implement.
    expectErrors("',' expected");
    parse("function foo({x}: string) {}");
  }

  public void testFunctionParamDeclaration_arrow() {
    Node fn = parse("(x: string) => 'hello' + x;").getFirstChild().getFirstChild();
    JSDocInfo paramInfo = fn.getFirstChild().getNext().getFirstChild().getJSDocInfo();
    assertTypeEquals(STRING_TYPE, paramInfo.getType());
  }

  public void testFunctionReturn() {
    Node fn = parse("function foo(): string { return 'hello'; }").getFirstChild();
    JSDocInfo fnDocInfo = fn.getJSDocInfo();
    assertTypeEquals(STRING_TYPE, fnDocInfo.getReturnType());
  }

  public void testFunctionReturn_arrow() {
    Node fn = parse("(): string => 'hello';").getFirstChild().getFirstChild();
    JSDocInfo fnDocInfo = fn.getJSDocInfo();
    assertTypeEquals(STRING_TYPE, fnDocInfo.getReturnType());
  }

  public void testCompositeType() {
    JSTypeExpression type = parseTypeOfVar("var foo: mymod.ns.Type;");
    JSType namedType = createNullableType(createNamedType("mymod.ns.Type"));
    assertTypeEquals(namedType, type);
  }

  public void testCompositeType_trailingDot() {
    expectErrors("'identifier' expected");
    parse("var foo: mymod.Type.;");
  }

  public void testArrayType() {
    JSTypeExpression parsedType = parseTypeOfVar("var foo: string[];");

    JSType arrayOfString = createNullableType(createTemplatizedType(ARRAY_TYPE, STRING_TYPE));
    assertTypeEquals(arrayOfString, parsedType);
  }

  public void testArrayType_missingClose() {
    expectErrors("']' expected");
    parse("var foo: string[;");
  }

  public void testArrayType_namespaced() {
    Node varDecl = parse("var foo: mymod.ns.Type[];").getFirstChild();
    JSTypeExpression parsedType = varDecl.getFirstChild().getJSDocInfo().getType();

    JSType arrayOfTypes =
        createNullableType(createTemplatizedType(ARRAY_TYPE,
            createNullableType(createNamedType("mymod.ns.Type"))));
    assertTypeEquals(arrayOfTypes, parsedType);
  }

  private JSTypeExpression parseTypeOfVar(String expr) {
    Node varDecl = parse(expr).getFirstChild();
    JSTypeExpression parsedType = varDecl.getFirstChild().getJSDocInfo().getType();
    return parsedType;
  }

  public void testParameterizedType() {
    JSTypeExpression type = parseTypeOfVar("var x: my.parameterized.Type<ns.A, ns.B>;");
    JSType expectedType =
        createNullableType(createTemplatizedType(createNamedType("my.parameterized.Type"),
            createNullableType(createNamedType("ns.A")),
            createNullableType(createNamedType("ns.B"))));
    assertTypeEquals(expectedType, type);
  }

  public void testParameterizedType_empty() {
    expectErrors("Unexpected end of type expression");
    parse("var x: my.parameterized.Type<ns.A, >;");
  }

  public void testParameterizedType_trailing1() {
    expectErrors("'>' expected");
    parse("var x: my.parameterized.Type<ns.A;");
  }

  public void testParameterizedType_trailing2() {
    expectErrors("Unexpected end of type expression");
    parse("var x: my.parameterized.Type<ns.A,;");
  }

  public void testMemberVariable() throws Exception {
    parse("class Foo { foo; }");
  }

  public void testComputedPropertyMemberVariable() throws Exception {
    parse("class Foo { ['foo']; }");
  }

  public void testMemberVariable_initialiser() throws Exception {
    // TODO(martinprobst): Implement.
    expectErrors("';' expected");
    parse("class Foo { foo = 12; }");
  }

  public void testMemberVariable_type() throws Exception {
    parse("class Foo { foo: number; }");
  }

  public void testMemberVariable_typeFn() throws Exception {
    Node ast = parse("class Foo {  /** @return {number} */ foo() { return 12; } }");
    System.out.println(ast);
    ast = parse("/** @type {string} */ Foo.prototype.bar;");
    System.out.println(ast);
  }

  public void testMemberVariable_typeInitialiser() throws Exception {
    // TODO(martinprobst): Implement.
    expectErrors("';' expected");
    parse("class Foo { foo: number = 12; }");
  }

  private NamedType createNamedType(String typeName) {
    return registry.createNamedType(typeName, null, -1, -1);
  }

  private Node parse(String string) {
    StaticSourceFile file = new SimpleSourceFile("input", false);
    Node script = ParserRunner.parse(file,
        string,
        ParserRunner.createConfig(false, LanguageMode.ECMASCRIPT6_STRICT, false, true, null),
        testErrorReporter).ast;

    // verifying that all warnings were seen
    assertTrue(testErrorReporter.hasEncounteredAllErrors());
    assertTrue(testErrorReporter.hasEncounteredAllWarnings());

    return script;
  }
}
