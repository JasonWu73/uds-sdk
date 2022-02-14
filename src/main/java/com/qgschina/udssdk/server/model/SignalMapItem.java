package com.qgschina.udssdk.server.model;

import java.lang.reflect.Method;
import lombok.Data;

/**
 * 用于"方法调用"和"信号触发" Map 的 value
 */
@Data
public class SignalMapItem {

  private Object service;

  private Method method;
}
