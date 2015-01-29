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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public class CodePrinterEs6TypedTest extends CodePrinterTestBase {

  @Override
  public void setUp() {
    super.setUp();
    languageMode = LanguageMode.ECMASCRIPT6_TYPED;
    singleQuoteStrings = true;
  }

  @Override
  void assertPrintSame(String js) {
    String parsed = parsePrint(js, true, CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD);
    parsed = parsed.trim(); // strip trailing line break.
    assertEquals(js, parsed);
  }

  public void testVariableDeclaration() {
    assertPrintSame("var foo: any = 'hello';");
    assertPrintSame("var foo: number = 'hello';");
    assertPrintSame("var foo: boolean = 'hello';");
    assertPrintSame("var foo: string = 'hello';");
    assertPrintSame("var foo: void = 'hello';");
    assertPrintSame("var foo: hello = 'hello';");
  }

  public void testFunctionParamDeclaration() {
    assertPrintSame("function foo(x: string) {\n}");
  }

  public void testFunctionParamDeclaration_defaultValue() {
    assertPrintSame("function foo(x: string = 'hello') {\n}");
  }

  public void testFunctionParamDeclaration_arrow() {
    assertPrintSame("(x: string) => 'hello' + x;");
  }

  public void testFunctionReturn() {
    assertPrintSame("function foo(): string {\n  return'hello';\n}");
  }

  public void testFunctionReturn_arrow() {
    assertPrintSame("(): string => 'hello';");
  }

  public void testCompositeType() {
    assertPrintSame("var foo: mymod.ns.Type;");
  }

  public void testArrayType() {
    assertPrintSame( "var foo: string[];");
  }

  public void testArrayType_qualifiedType() {
    assertPrintSame( "var foo: mymod.ns.Type[];");
  }

  public void testParameterizedType() {
    assertPrintSame("var x: my.parameterized.Type<ns.A, ns.B>;");
  }
}
