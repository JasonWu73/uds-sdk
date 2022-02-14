package com.qgschina.udssdk.common.util;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 反射相关的工具类
 */
public class ReflectionUtils {

  /**
   * 获取方法的参数名列表
   *
   * @param method 方法对象
   * @return 参数名列表
   */
  public static List<String> getParamNames(Method method) {
    return Arrays.stream(method.getParameters())
        .map(Parameter::getName)
        .collect(Collectors.toList());
  }

  /**
   * 获取方法的参数类型的统一标识列表
   *
   * @param method 方法对象
   * @return 参数类型的统一标识列表
   */
  public static List<String> getParamTypes(Method method) {
    return Arrays.stream(method.getParameterTypes())
        .map(clazz -> {
          Optional<String> typeName = DataTypeUtils
              .getGeneralParamTypeName(clazz);
          return typeName.orElse("unknown");
        })
        .collect(Collectors.toList());
  }
}
