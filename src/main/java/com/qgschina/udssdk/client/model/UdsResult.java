package com.qgschina.udssdk.client.model;

import com.qgschina.udssdk.client.constant.UdsCode;
import lombok.Data;

/**
 * UDS 调用返回结果
 *
 * @param <T> 最终返回值类型
 */
@Data
public class UdsResult<T> {

  /**
   * 响应码
   */
  private UdsCode code;

  /**
   * 提示消息
   */
  private String message;

  /**
   * 返回值
   */
  private T data;
}
