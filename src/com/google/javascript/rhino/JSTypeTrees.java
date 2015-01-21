package com.google.javascript.rhino;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

import java.util.Iterator;

/**
 * Created by alexeagle on 1/20/15.
 */
public class JSTypeTrees {

  /**
   * The root of a JSTypeExpression is very different from an AST node, even
   * though we use the same Java class to represent them.
   * This function converts root nodes of JSTypeExpressions, to make them more
   * similar to ordinary AST nodes.
   */
  // TODO(dimvar): Eventually, we want to just parse types to the new
  // representation directly, and delete this function.
  public static Node convertTypeNodeAST(Node n) {
    int token = n.getType();
    switch (token) {
      case Token.STAR:
      case Token.VOID:
      case Token.EMPTY: // for function types that don't declare a return type
        return new Node(token);
      case Token.BANG:
        return new Node(Token.BANG, convertTypeNodeAST(n.getFirstChild()));
      case Token.STRING:
        String typeName = n.getString();
        switch (typeName) {
          case "boolean":
            return new Node(Token.BOOLEAN_TYPE);
          case "null":
            return new Node(Token.NULL);
          case "number":
            return new Node(Token.NUMBER_TYPE);
          case "string":
            return new Node(Token.STRING);
          case "undefined":
          case "void":
            return new Node(Token.VOID);
          default:
            Node root = parseQualifiedName(typeName);
            if (n.getChildCount() > 0 && n.getFirstChild().isBlock()) {
              root = new Node(Token.PARAMETERIZED_TYPE, root);
              for (Node typeParameter : n.getFirstChild().children()) {
                root.addChildToBack(convertTypeNodeAST(typeParameter));
              }
            }
            return root;
        }
      case Token.QMARK:
        Node child = n.getFirstChild();
        if (child == null) {
          return new Node(Token.QMARK);
        }
        return new Node(
            Token.UNION_TYPE,
            new Node(Token.NULL),
            convertTypeNodeAST(child));
      case Token.LC:
        return convertRecordTypeAST(n.getFirstChild());
      case Token.PIPE:
        // union types
        Node unionNode = new Node(Token.UNION_TYPE);
        for (Node child2 : n.children()) {
          unionNode.addChildToBack(convertTypeNodeAST(child2));
        }
        return unionNode;
      case Token.FUNCTION:
        Node result = new Node(Token.FUNCTION_TYPE);
        for (Node child2 : n.children()) {
          if (child2.isParamList()) {
            int paramIdx = 1;
            for (Node param : child2.children()) {
              Node stringKey = IR.stringKey("p" + paramIdx++);
              stringKey.addChildToFront(convertTypeNodeAST(param));
              result.addChildToBack(stringKey);
            }
          } else if (child2.isThis()) {
            Node stringKey = IR.stringKey("this");
            stringKey.addChildToFront(convertTypeNodeAST(child2.getFirstChild()));
            result.addChildToBack(stringKey);
          } else {
            result.addChildToFront(convertTypeNodeAST(child2));
          }
        }

        return result;
      default:
        throw new IllegalArgumentException(
            "Unsupported node type: " + Token.name(n.getType())
                + " " + n.toStringTree());
    }
  }

  private static Node parseQualifiedName(String typeName) {
    Iterator<String> parts = Splitter.on(".").split(typeName).iterator();
    Node result = IR.name(parts.next());
    while (parts.hasNext()) {
      result = IR.getprop(result, IR.string(parts.next()));
    }
    return result;
  }

  private static Node convertRecordTypeAST(Node n) {
    Node objectLit = new Node(Token.OBJECTLIT);
    for (Node field = n.getFirstChild();
         field != null;
         field = field.getNext()) {
      boolean isFieldTypeDeclared = field.getType() == Token.COLON;
      Node fieldNameNode = isFieldTypeDeclared ? field.getFirstChild() : field;
      String fieldName = fieldNameNode.getString();
      if (fieldName.startsWith("'") || fieldName.startsWith("\"")) {
        fieldName = fieldName.substring(1, fieldName.length() - 1);
      }
      Node fieldType = isFieldTypeDeclared
          ? convertTypeNodeAST(field.getLastChild()) : new Node(Token.QMARK);
      Node newField = IR.stringKey(fieldName);
      newField.addChildToBack(fieldType);
      objectLit.addChildToBack(newField);
    }
    return objectLit;
  }
}
