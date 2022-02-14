package com.qgschina.udssdk.common.util;

import com.qgschina.udssdk.common.constant.InnerParamType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 各语言间的数据类型统一标识工具类
 */
public class DataTypeUtils {

  /**
   * 获取数据类型的统一标识
   *
   * @param obj 数据对象
   * @return 统一标识
   */
  public static Optional<String> getGeneralParamTypeName(Object obj) {
    if (checkIfArrayButNotBytes(obj.getClass())) {
      return Optional.empty();
    }

    if (obj instanceof String || checkIfByteArray(obj.getClass())) {
      return Optional.of(InnerParamType.STRING.value());
    }

    if (obj instanceof Integer || obj instanceof Long) {
      return Optional.of(InnerParamType.INT.value());
    }

    if (obj instanceof Double || obj instanceof Float) {
      return Optional.of(InnerParamType.FLOAT.value());
    }

    if (obj instanceof Boolean) {
      return Optional.of(InnerParamType.BOOL.value());
    }

    if (obj instanceof List || obj.getClass().isArray()) {
      return Optional.of(InnerParamType.LIST.value());
    }

    // 因为 POJO 较特殊, 故最后判断
    if (obj instanceof Map || JsonUtils.checkIfCanSerialize(obj.getClass())) {
      return Optional.of(InnerParamType.MAP.value());
    }

    return Optional.empty();
  }

  /**
   * 获取数据类型的统一标识
   *
   * @param paramClass 数据类类型
   * @return 统一标识
   */
  public static Optional<String> getGeneralParamTypeName(Class<?> paramClass) {
    if (checkIfArrayButNotBytes(paramClass)) {
      return Optional.empty();
    }

    if (checkIfSameClassName(String.class, paramClass) ||
        checkIfByteArray(paramClass)) {
      return Optional.of(InnerParamType.STRING.value());
    }

    if (checkIfSameClassName(Integer.class, paramClass) ||
        checkIfSameClassName(Long.class, paramClass) ||
        Objects.equals("int", paramClass.getSimpleName())) {
      return Optional.of(InnerParamType.INT.value());
    }

    if (checkIfSameClassName(Double.class, paramClass) ||
        checkIfSameClassName(Float.class, paramClass)) {
      return Optional.of(InnerParamType.FLOAT.value());
    }

    if (checkIfSameClassName(Boolean.class, paramClass)) {
      return Optional.of(InnerParamType.BOOL.value());
    }

    if (checkIfList(paramClass)) {
      return Optional.of(InnerParamType.LIST.value());
    }

    // 因为 POJO 较特殊, 故最后判断
    if (checkIfMap(paramClass) ||
        JsonUtils.checkIfCanSerialize(paramClass)) {
      return Optional.of(InnerParamType.MAP.value());
    }

    return Optional.empty();
  }

  /**
   * 判断类型是否为字节数组 ({@code byte[]})
   *
   * @param clazz 类类型
   * @return {@code true} 若类型为 {@code byte[]}
   */
  public static boolean checkIfByteArray(Class<?> clazz) {
    return Objects.equals("byte[]", clazz.getSimpleName());
  }

  /**
   * 判断类型是数组但不是字节数组
   *
   * @param clazz 类类型
   * @return {@code true} 若类型是数组但不是字节数组
   */
  private static boolean checkIfArrayButNotBytes(Class<?> clazz) {
    return !checkIfByteArray(clazz) && clazz.isArray();
  }

  /**
   * 判断两个类类型的名称是否相同 (忽略大小写)
   *
   * @param c1 class 1
   * @param c2 class 2
   * @return {@code true} 若两个类类型的名称相同
   */
  private static boolean checkIfSameClassName(Class<?> c1, Class<?> c2) {
    return c1.getSimpleName().equalsIgnoreCase(c2.getSimpleName());
  }

  /**
   * 判断类型是否为 {@link List}
   *
   * @param clazz 类类型
   * @return {@code true} 若类型是 {@link List}
   */
  private static boolean checkIfList(Class<?> clazz) {
    if (Objects.equals("List", clazz.getSimpleName())) {
      return true;
    }

    Type superClass = clazz.getGenericSuperclass();
    if (superClass == null) {
      return false;
    }

    return superClass.toString().toLowerCase(Locale.ROOT)
        .contains("list");
  }

  /**
   * 判断类型是否为 {@link Map}
   *
   * @param clazz 类类型
   * @return {@code true} 若类型是 {@link Map}
   */
  private static boolean checkIfMap(Class<?> clazz) {
    if (Objects.equals("Map", clazz.getSimpleName())) {
      return true;
    }

    Type superClass = clazz.getGenericSuperclass();
    if (superClass == null) {
      return false;
    }

    return superClass.toString().toLowerCase(Locale.ROOT)
        .contains("map");
  }
}
