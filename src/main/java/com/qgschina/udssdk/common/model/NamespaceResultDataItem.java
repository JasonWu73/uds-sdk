package com.qgschina.udssdk.common.model;

import java.util.List;
import lombok.Data;

/**
 * 获取地址空间返回时的具体数据项
 */
@Data
public class NamespaceResultDataItem {

  /**
   * 方法名或信号号
   */
  private String name;

  /**
   * 参数名列表
   */
  private List<String> parameterNames;

  /**
   * 参数类型列表
   */
  private List<String> parameterTypes;
}
