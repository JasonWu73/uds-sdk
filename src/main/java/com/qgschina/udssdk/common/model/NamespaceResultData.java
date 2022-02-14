package com.qgschina.udssdk.common.model;

import java.util.List;
import lombok.Data;

/**
 * 获取地址空间返回时的数据结果包装对象
 */
@Data
public class NamespaceResultData {

  /**
   * 可调用方法列表
   */
  private List<NamespaceResultDataItem> method;

  /**
   * 可调用方法数量
   */
  private Integer methodNum;

  /**
   * 可触发信号列表
   */
  private List<NamespaceResultDataItem> signal;
}
