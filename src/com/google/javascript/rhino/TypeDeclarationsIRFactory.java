package com.google.javascript.rhino;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.javascript.rhino.Node.TypeDeclarationNode;

/**
 * Produces ASTs which represent JavaScript type declarations, both those created from
 * closure-style type declarations in a JSDoc node (via a conversion from the rhino AST
 * produced in {@link com.google.javascript.jscomp.parsing.NewIRFactory}) as well as
 * those created from TypeScript-style inline type declarations.
 */
public class TypeDeclarationsIRFactory {

  public static TypeDeclarationNode stringType() {
    return new TypeDeclarationNode(Token.STRING_TYPE);
  }

  public static TypeDeclarationNode numberType() {
    return new TypeDeclarationNode(Token.NUMBER_TYPE);
  }

  public static TypeDeclarationNode booleanType() {
    return new TypeDeclarationNode(Token.BOOLEAN_TYPE);
  }

  public static TypeDeclarationNode nullType() {
    return new TypeDeclarationNode(Token.NULL_TYPE);
  }

  public static TypeDeclarationNode anyType() {
    return new TypeDeclarationNode(Token.ANY_TYPE);
  }

  public static TypeDeclarationNode voidType() {
    return new TypeDeclarationNode(Token.VOID_TYPE);
  }

  public static TypeDeclarationNode unknownType() {
    return new TypeDeclarationNode(Token.UNKNOWN_TYPE);
  }

  public static TypeDeclarationNode namedType(String typeName) {
    Iterator<String> parts = Splitter.on(".").split(typeName).iterator();
    Node node = IR.name(parts.next());
    while (parts.hasNext()) {
      node = IR.getprop(node, IR.string(parts.next()));
    }
    return new TypeDeclarationNode(Token.NAMED_TYPE, node);
  }

  public static TypeDeclarationNode recordType(LinkedHashMap<String, TypeDeclarationNode> properties) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.OBJECTLIT);
    for (Map.Entry<String, TypeDeclarationNode> property : properties.entrySet()) {
      Node stringKey = IR.stringKey(property.getKey());
      stringKey.addChildToFront(property.getValue());
      node.addChildToBack(stringKey);
    }
    return node;
  }

  public static TypeDeclarationNode functionType(Node returnType, LinkedHashMap<String, TypeDeclarationNode> parameters) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.FUNCTION_TYPE, returnType);
    for (Map.Entry<String, TypeDeclarationNode> parameter : parameters.entrySet()) {
      Node stringKey = IR.stringKey(parameter.getKey());
      stringKey.addChildToFront(parameter.getValue());
      node.addChildToBack(stringKey);
    }
    return node;
  }

  public static TypeDeclarationNode parameterizedType(Node baseType, Iterable<TypeDeclarationNode> typeParameters) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.PARAMETERIZED_TYPE, baseType);
    for (Node typeParameter : typeParameters) {
      node.addChildToBack(typeParameter);
    }
    return node;
  }

  public static TypeDeclarationNode unionType(Iterable<TypeDeclarationNode> options) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.UNION_TYPE);
    for (Node option : options) {
      node.addChildToBack(option);
    }
    return node;
  }

  public static TypeDeclarationNode unionType(TypeDeclarationNode... options) {
    return unionType(Arrays.asList(options));
  }

  public static TypeDeclarationNode restParams(TypeDeclarationNode type) {
    return new TypeDeclarationNode(Token.REST_PARAMETER_TYPE, type);
  }

  // Allow functional-style Iterables.transform over collections of nodes.
  private static final Function<Node, TypeDeclarationNode> CONVERT_TYPE_NODE =
      new Function<Node, TypeDeclarationNode>() {
        @Override
        public TypeDeclarationNode apply(Node node) {
          return convertTypeNodeAST(node);
        }
      };

  /**
   * The root of a JSTypeExpression is very different from an AST node, even
   * though we use the same Java class to represent them.
   * This function converts root nodes of JSTypeExpressions into TypeDeclaration ASTs,
   * to make them more similar to ordinary AST nodes.
   */
  // TODO(dimvar): Eventually, we want to just parse types to the new
  // representation directly, and delete this function.
  public static TypeDeclarationNode convertTypeNodeAST(Node n) {
    int token = n.getType();
    switch (token) {
      case Token.STAR:
        return unknownType();
      case Token.VOID:
        return voidType();
      case Token.EMPTY: // for function types that don't declare a return type
        return unknownType();
      case Token.BANG:
        TypeDeclarationNode node = convertTypeNodeAST(n.getFirstChild());

        // FIXME: should nullability be in the type AST? probably
        node.putBooleanProp(Node.NULLABLE_TYPE, false);
        return node;
      case Token.STRING:
        String typeName = n.getString();
        switch (typeName) {
          case "boolean":
            return booleanType();
          case "null":
            return nullType();
          case "number":
            return numberType();
          case "string":
            return stringType();
          case "undefined":
            return unknownType();
          case "void":
            return voidType();
          default:
            TypeDeclarationNode root = namedType(typeName);
            if (n.getChildCount() > 0 && n.getFirstChild().isBlock()) {
              return parameterizedType(root,
                  Iterables.transform(n.getFirstChild().children(), CONVERT_TYPE_NODE));
            }
            return root;
        }
      case Token.QMARK:
        Node child = n.getFirstChild();
        return child == null ? anyType() : unionType(nullType(), convertTypeNodeAST(child));
      case Token.LC:
        LinkedHashMap<String, TypeDeclarationNode> properties = new LinkedHashMap<>();
        for (Node field : n.getFirstChild().children()) {
          boolean isFieldTypeDeclared = field.getType() == Token.COLON;
          Node fieldNameNode = isFieldTypeDeclared ? field.getFirstChild() : field;
          String fieldName = fieldNameNode.getString();
          if (fieldName.startsWith("'") || fieldName.startsWith("\"")) {
            fieldName = fieldName.substring(1, fieldName.length() - 1);
          }
          TypeDeclarationNode fieldType = isFieldTypeDeclared
              ? convertTypeNodeAST(field.getLastChild()) : unknownType();
          properties.put(fieldName, fieldType);
        }
        return recordType(properties);
      case Token.PIPE:
        return unionType(Iterables.transform(n.children(), CONVERT_TYPE_NODE));
      case Token.ELLIPSIS:
        return restParams(convertTypeNodeAST(n.getFirstChild()));
      case Token.FUNCTION:
        Node returnType = unknownType();
        LinkedHashMap<String, TypeDeclarationNode> parameters = new LinkedHashMap<>();
        for (Node child2 : n.children()) {
          if (child2.isParamList()) {
            int paramIdx = 1;
            for (Node param : child2.children()) {
              parameters.put("p" + paramIdx++, convertTypeNodeAST(param));
            }
          } else if (child2.isThis() || child2.isNew()) {
            // These aren't expressable in TypeScript syntax, so we omit them from the tree.
            // They could be added as properties on the result node.
          } else if (child2.getType() == Token.ELLIPSIS) {
          } else {
            returnType = convertTypeNodeAST(child2);
          }
        }
        return functionType(returnType, parameters);
      default:
        throw new IllegalArgumentException(
            "Unsupported node type: " + Token.name(n.getType())
                + " " + n.toStringTree());
    }
  }

}
