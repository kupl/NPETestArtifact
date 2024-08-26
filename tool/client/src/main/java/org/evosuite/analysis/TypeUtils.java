package org.evosuite.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.chain.CtQuery;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeUtils {
  private static final Logger logger = LoggerFactory.getLogger(TypeUtils.class);

  private TypeUtils() {
  }

  static CtTypeReference<Boolean> booleanBoxingType;
  static CtTypeReference<Byte> byteBoxingType;
  static CtTypeReference<Integer> integerBoxingType;
  static CtTypeReference<Short> shortBoxingType;
  static CtTypeReference<Long> longBoxingType;
  static CtTypeReference<Float> floatBoxingType;
  static CtTypeReference<Double> doubleBoxingType;
  static CtTypeReference<Character> charBoxingType;

  static CtTypeReference<Boolean> booleanPrimitive;
  static CtTypeReference<Byte> bytePrimitive;
  static CtTypeReference<Integer> integerPrimitive;
  static CtTypeReference<Short> shortPrimitive;
  static CtTypeReference<Long> longPrimitive;
  static CtTypeReference<Float> floatPrimitive;
  static CtTypeReference<Double> doublePrimitive;
  static CtTypeReference<Character> charPrimitive;

  static CtTypeReference<String> stringType;
  static CtTypeReference<Object> objectType;

  static CtTypeReference<?> nullType;
  static CtWildcardReference wildcard;
  static CtTypeReference<?> voidPrimitive;

  static CtTypeReference<?> listType;
  static CtTypeReference<?> mapType;
  static CtTypeReference<?> setType;

  static final List<CtTypeReference<?>> defaultTypeParameters = new ArrayList<>();

  public static void setup(Factory factory) {
    booleanBoxingType = factory.Type().BOOLEAN;
    byteBoxingType = factory.Type().BYTE;
    shortBoxingType = factory.Type().SHORT;
    integerBoxingType = factory.Type().INTEGER;
    longBoxingType = factory.Type().LONG;
    floatBoxingType = factory.Type().FLOAT;
    doubleBoxingType = factory.Type().DOUBLE;
    charBoxingType = factory.Type().CHARACTER;

    booleanPrimitive = factory.Type().BOOLEAN_PRIMITIVE;
    bytePrimitive = factory.Type().BYTE_PRIMITIVE;
    shortPrimitive = factory.Type().SHORT_PRIMITIVE;
    integerPrimitive = factory.Type().INTEGER_PRIMITIVE;
    longPrimitive = factory.Type().LONG_PRIMITIVE;
    floatPrimitive = factory.Type().FLOAT_PRIMITIVE;
    doublePrimitive = factory.Type().DOUBLE_PRIMITIVE;
    charPrimitive = factory.Type().CHARACTER_PRIMITIVE;

    stringType = factory.Type().STRING;
    objectType = factory.Type().OBJECT;

    nullType = factory.Type().NULL_TYPE;
    voidPrimitive = factory.Type().VOID_PRIMITIVE;
    wildcard = factory.createWildcardReference();

    listType = factory.Type().createReference(List.class);
    mapType = factory.Type().createReference(Map.class);
    setType = factory.Type().createReference(Set.class);

    defaultTypeParameters.add(stringType);
    defaultTypeParameters.add(integerBoxingType);
    defaultTypeParameters.add(objectType);
  }

  /**
   * Return true if the type is a raw type.
   * @param typeReference the type to check.
   * @return true if the type is a raw type. false if the type is a parameterized type, a type that cannot be
   * parameterized, or a type whose declaration is not found.
   */
  public static boolean isRawType(CtTypeReference<?> typeReference) {
    if (typeReference instanceof CtTypeParameterReference) {
      return false;
    }

    if (typeReference.isArray()) {
      return false;
    }

    if (typeReference.getTypeDeclaration() == null) {
      logger.debug("**** Cannot find declaration of type: {}", typeReference.getQualifiedName());
      return false;
    }

    return typeReference.getTypeDeclaration().isGenerics() && !typeReference.isParameterized();
  }

  /**
   * Fill raw type with wildcards.
   */
  public static CtTypeReference<?> parameterizedTypeOfRawType(CtTypeReference<?> typeReference) {
    if (typeReference instanceof CtTypeParameterReference) {
      return null;
    }
    final CtTypeReference<?> result = typeReference.clone();
    List<CtTypeReference<?>> typeArguments = new ArrayList<>();
    for (int i = 0; i < typeReference.getTypeDeclaration().getFormalCtTypeParameters().size(); i++) {
      typeArguments.add(wildcard.clone());
    }
    return result.setActualTypeArguments(typeArguments);
  }

  public static List<CtTypeReference<?>> getSuperTypes(CtTypeReference<?> type) {
    List<CtTypeReference<?>> superTypes = new ArrayList<>();
    CtTypeReference<?> superclass = type.getSuperclass();
    if (superclass != null) {
      superTypes.add(superclass);
    }
    Set<CtTypeReference<?>> superInterfaces = type.getSuperInterfaces();
    if (superInterfaces != null) {
      superTypes.addAll(superInterfaces);
    }
    return superTypes;
  }

  public static List<CtTypeReference<?>> getReferableSubtypes(CtTypeReference<?> type) {
    List<CtTypeReference<?>> results = new ArrayList<>();
    if (type instanceof CtTypeParameterReference) {
      results.add(type);
      return results;
    }

    return results;
  }

  public static boolean isBoolean(CtTypeReference<?> primitiveType) {
    return booleanPrimitive.equals(primitiveType.unbox());
  }

  public static boolean isByte(CtTypeReference<?> primitiveType) {
    return bytePrimitive.equals(primitiveType.unbox());
  }

  public static boolean isShort(CtTypeReference<?> primitiveType) {
    return shortPrimitive.equals(primitiveType.unbox());
  }

  public static boolean isInteger(CtTypeReference<?> primitiveType) {
    return integerPrimitive.equals(primitiveType.unbox());
  }

  public static boolean isLong(CtTypeReference<?> primitiveType) {
    return longPrimitive.equals(primitiveType.unbox());
  }

  public static boolean isFloat(CtTypeReference<?> primitiveType) {
    return floatPrimitive.equals(primitiveType.unbox());
  }

  public static boolean isDouble(CtTypeReference<?> primitiveType) {
    return doublePrimitive.equals(primitiveType.unbox());
  }

  public static boolean isChar(CtTypeReference<?> primitiveType) {
    return charPrimitive.equals(primitiveType.unbox());
  }

  public static boolean isString(CtTypeReference<?> primitiveType) {
    return primitiveType.equals(stringType);
  }

  public static boolean isNull(CtTypeReference<?> typeReference) {
    if (typeReference == null) return true;
    return typeReference.equals(nullType);
  }

  public static List<Integer> getWildcardIndices(CtTypeReference<?> declarationType) {
    List<CtTypeReference<?>> actualTypeArguments = declarationType.getActualTypeArguments();
    List<Integer> wildcardIndices = new ArrayList<>();
    int i = 0;
    for (CtTypeReference<?> actualTypeArgument : actualTypeArguments) {
      if (actualTypeArgument instanceof CtWildcardReference) {
        CtTypeReference<?> boundingType = ((CtWildcardReference) actualTypeArgument).getBoundingType();
        if (boundingType == null ||
                (isJavaLangObject(boundingType) && boundingType.isImplicit())) {
          wildcardIndices.add(i);
        }
      }
      i++;
    }
    return wildcardIndices;
  }

  public static boolean isNonPrimitiveArray(CtTypeReference<?> typeReference) {
    return typeReference.isArray() && typeReference.getTypeDeclaration() == null;
  }

  public static boolean isPrimitiveArray(CtTypeReference<?> type) {
    if (!type.isArray()) {
      return false;
    }
    CtArrayTypeReference<?> arrayType = (CtArrayTypeReference<?>) type;
    CtTypeReference<?> componentType = arrayType.getComponentType();
    return isPrimitive(componentType);
  }

  public static boolean isPrimitive(CtType<?> ctType) {
    if (ctType == null) return false;
    return isPrimitive(ctType.getReference());
  }

  public static boolean isPrimitive(CtTypeReference<?> type) {
    if (type == null) return false;
    return type.isPrimitive();
  }

  public static boolean isJavaLangClass(CtTypeReference<?> ctTypeReference) {
    return ctTypeReference.getQualifiedName().equals("java.lang.Class");
  }


  public static boolean isJavaLangObject(CtTypeReference<?> ctTypeReference) {
    return ctTypeReference.getQualifiedName().equals("java.lang.Object");
  }

  public static boolean isJavaLangEnum(CtTypeReference<?> type) {
    return type.getQualifiedName().equals("java.lang.Enum");
  }

  public static boolean isVoidPrimitive(CtTypeReference<?> type) {
    return type.equals(voidPrimitive());
  }

  public static boolean isBoxingType(CtTypeReference<?> instanceType) {
    return isBooleanBoxingType(instanceType)
            || isByteBoxingType(instanceType)
            || isCharacterBoxingType(instanceType)
            || isShortBoxingType(instanceType)
            || isIntegerBoxingType(instanceType)
            || isLongBoxingType(instanceType)
            || isFloatBoxingType(instanceType)
            || isDoubleBoxingType(instanceType);
  }

  private static boolean isBooleanBoxingType(CtTypeReference<?> instanceType) {
    return instanceType.getQualifiedName().equals(booleanBoxingType.getQualifiedName());
  }

  private static boolean isByteBoxingType(CtTypeReference<?> instanceType) {
    return instanceType.getQualifiedName().equals(byteBoxingType.getQualifiedName());
  }

  private static boolean isCharacterBoxingType(CtTypeReference<?> instanceType) {
    return instanceType.getQualifiedName().equals(charBoxingType.getQualifiedName());
  }

  private static boolean isShortBoxingType(CtTypeReference<?> instanceType) {
    return instanceType.getQualifiedName().equals(shortBoxingType.getQualifiedName());
  }

  private static boolean isIntegerBoxingType(CtTypeReference<?> instanceType) {
    return instanceType.getQualifiedName().equals(integerBoxingType.getQualifiedName());
  }

  private static boolean isLongBoxingType(CtTypeReference<?> instanceType) {
    return instanceType.getQualifiedName().equals(longBoxingType.getQualifiedName());
  }

  private static boolean isFloatBoxingType(CtTypeReference<?> instanceType) {
    return instanceType.getQualifiedName().equals(floatBoxingType.getQualifiedName());
  }

  private static boolean isDoubleBoxingType(CtTypeReference<?> instanceType) {
    return instanceType.getQualifiedName().equals(doubleBoxingType.getQualifiedName());
  }

  public static String toQualifiedName(CtTypeReference<?> ctTypeReference) {
    return isPrimitive(ctTypeReference) ? ctTypeReference.unbox().getSimpleName() : ctTypeReference.getQualifiedName();
  }

  public static String toQualifiedName(CtType<?> ctType) {
    return toQualifiedName(ctType.getReference());
  }

  public static CtTypeReference<Boolean> booleanBoxingType() {
    return booleanBoxingType;
  }

  public static CtTypeReference<Byte> byteBoxingType() {
    return byteBoxingType;
  }

  public static CtTypeReference<Integer> integerBoxingType() {
    return integerBoxingType;
  }

  public static CtTypeReference<Short> shortBoxingType() {
    return shortBoxingType;
  }

  public static CtTypeReference<Long> longBoxingType() {
    return longBoxingType;
  }

  public static CtTypeReference<Float> floatBoxingType() {
    return floatBoxingType;
  }

  public static CtTypeReference<Double> doubleBoxingType() {
    return doubleBoxingType;
  }

  public static CtTypeReference<Character> charBoxingType() {
    return charBoxingType;
  }

  public static CtTypeReference<Boolean> booleanPrimitive() {
    return booleanPrimitive;
  }

  public static CtTypeReference<?> bytePrimitive() {
    return bytePrimitive;
  }

  public static CtTypeReference<Integer> integerPrimitive() {
    return integerPrimitive;
  }

  public static CtTypeReference<Short> shortPrimitive() {
    return shortPrimitive;
  }

  public static CtTypeReference<Long> longPrimitive() {
    return longPrimitive;
  }

  public static CtTypeReference<Float> floatPrimitive() {
    return floatPrimitive;
  }

  public static CtTypeReference<Double> doublePrimitive() {
    return doublePrimitive;
  }

  public static CtTypeReference<Character> charPrimitive() {
    return charPrimitive;
  }

  public static CtTypeReference<String> stringType() {
    return stringType;
  }

  public static CtTypeReference<Object> objectType() {
    return objectType;
  }

  public static CtTypeReference<?> nullType() {
    return nullType;
  }

  public static CtWildcardReference wildcard() {
    return wildcard;
  }

  public static CtTypeReference<?> voidPrimitive() {
    return voidPrimitive;
  }

  public static CtTypeReference<?> listType() {
    return listType;
  }

  public static CtTypeReference<?> mapType() {
    return mapType;
  }

  public static CtTypeReference<?> setType() {
    return setType;
  }

  public static List<CtTypeReference<?>> defaultTypeParameters() {
    return defaultTypeParameters;
  }

  public static List<CtTypeReference<?>> gatherTypeArguments(CtQuery baseObjectType) {
    return baseObjectType.select(t -> t.getRoleInParent().equals(CtRole.TYPE_ARGUMENT)).list();
  }

  public static List<CtWildcardReference> getWildcardArguments(CtTypeReference<?> typeReference) {
    return typeReference.filterChildren(new TypeFilter<>(CtWildcardReference.class))
            .select((CtWildcardReference w) -> w.getBoundingType() == null ||
                    (isJavaLangObject(w.getBoundingType()) && w.getBoundingType().isImplicit()))
            .list();
  }
}