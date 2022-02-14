package com.qgschina.udssdk.common.model;

import com.qgschina.udssdk.common.constant.InnerErrorCode;
import com.qgschina.udssdk.common.constant.InnerSubRespTypeCode;
import lombok.Data;

/**
 * SDK Server 内部返回数据
 */
@Data
public class InnerRespData<T> {

  /**
   * 错误代码, 详见 {@link InnerErrorCode#value()}
   */
  private Integer code;

  /**
   * 提示消息
   */
  private String msg;

  /**
   * 具体的返回结果
   * <p>
   * 注意: 若是异步调用 (信号触发), 则没有该字段
   */
  private T data;

  /**
   * 信号订阅类型, 详见 {@link InnerSubRespTypeCode#value()}
   * <p>
   * 注意: 方法调用和信号触发都没有该字段
   */
  private String type;
}
