package com.umasuo.device.center.application.service;

import com.umasuo.device.center.application.dto.DeviceMessage;
import com.umasuo.device.center.infrastructure.configuration.AppConfig;
import com.umasuo.device.center.infrastructure.exception.GeneratePasswordException;
import com.umasuo.device.center.infrastructure.exception.SubDeviceTopicException;
import com.umasuo.device.center.infrastructure.util.DevicePasswordUtils;
import com.umasuo.device.center.infrastructure.util.JsonUtils;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by umasuo on 17/6/27.
 */
@Service
public class MessageApplication implements CommandLineRunner {
  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(MessageApplication.class);

  private transient AppConfig appConfig;

  private transient MQTT mqtt;

  private transient BlockingConnection connection;

  private static final String USERNAME_PREFIX = "mqtt_user:";
  private static final String DEVICE_TOPIC_SUB_PREFIX = "device/sub/";
  private static final String DEVICE_TOPIC_PUB_PREFIX = "device/pub/";

  private static final String USER_TOPIC_SUB_PREFIX = "user/sub/";
  private static final String USER_TOPIC_PUB_PREFIX = "user/pub/";

  /**
   * 所有此service监听的topic
   */
  private List<Topic> topics = new ArrayList<>();
  /**
   * redis ops.
   */
  private transient StringRedisTemplate redisTemplate;


  private transient DeviceMessageHandler deviceMessageHandler;


  /**
   * 初始化和message broker的连接.
   *
   * @param appConfig 系统配置
   */
  @Autowired
  public MessageApplication(StringRedisTemplate redisTemplate,
                            DeviceMessageHandler deviceMessageHandler,
                            AppConfig appConfig
  ) {
    this.appConfig = appConfig;
    this.redisTemplate = redisTemplate;
    this.deviceMessageHandler = deviceMessageHandler;
    //自动添加用户名密码，保证其可以
    redisTemplate.boundHashOps(USERNAME_PREFIX + appConfig.getUsername()).put("password",
        appConfig.getPassword());

    mqtt = new MQTT();
    mqtt.setUserName(appConfig.getUsername());
    mqtt.setPassword(appConfig.getPassword());

    try {
      mqtt.setHost(appConfig.msgBrokerHost, appConfig.getMsgBrokerPort());
      connection = mqtt.blockingConnection();
      connection.connect();
      //todo service重启时，应该可以直接重新subscribe自己需要的topic
      logger.info("Connect to message broker: " + appConfig.getMsgBrokerHost());
    } catch (Exception e) {
      logger.error("Connect message broker failed.", e);
    }
  }

  /**
   * 发布消息.
   *
   * @param topic   topic，如果是设备的topic，则为topicID
   * @param payload 内容
   * @param qos     消息发送等级（0，1，2）
   * @param retain  是否保持在broker上
   */
  public void publish(final String topic, final byte[] payload, final QoS qos, final boolean
      retain) {
    logger.debug("Enter. topic: {}, payload: {}, qos: {}, retain: {}.", topic, new String
        (payload), qos, retain);
    try {
      connection.publish(topic, payload, qos, retain);
    } catch (Exception e) {
      logger.error("publish message failed.", e);
    }
  }

  /**
   * 下发一条message到设备上.
   *
   * @param deviceId 设备ID
   * @param userId   用户ID
   */
  public void publish(String deviceId, String userId, DeviceMessage message) {
    logger.debug("Enter. deviceId: {}, userId: {}, message: {}.", deviceId, userId, message);
    //组织每个用户的App只订阅自己的那一个topic,对topic内容的解析交给程序自己
    String topic = DEVICE_TOPIC_SUB_PREFIX + deviceId;
    String msg = JsonUtils.serialize(message);
    publish(topic, msg.getBytes(), QoS.AT_LEAST_ONCE, false);
  }

  /**
   * 在redis中添加可以连接上来的用户名和密码.
   *
   * @param username  用户名为设备的ID
   * @param publicKey password 为下发到设备的token
   */
  public void addMqttUser(String username, String publicKey) {
    logger.debug("Add broker user: {}.", username);
    try {
      String password = DevicePasswordUtils.getPassword(publicKey);
      if (password == null) {
        throw new GeneratePasswordException("Generate device password failed.");
      }
      Topic topic = new Topic(DEVICE_TOPIC_PUB_PREFIX + username, QoS.AT_LEAST_ONCE);
      topics.remove(topic);//如果已经存在，先移除，再重新添加
      topics.add(topic);

      connection.subscribe(topics.toArray(new Topic[topics.size()]));
      BoundHashOperations setOperations = redisTemplate.boundHashOps(USERNAME_PREFIX + username);
      //TODO MQTT 的的密码需要采用加密模式
      //TODO 这里其实需要考虑redis失效的场景
      setOperations.put("password", password);//添加用户名密码
    } catch (Exception e) {
      logger.error("Subscribe device topic failed. deviceId : {}", username, e);
      throw new SubDeviceTopicException("Subscribe device topic failed. deviceId : " + username);
    }
  }

  /**
   * Service 启动时自动接受
   *
   * @param args
   * @throws Exception
   */
  @Override
  public void run(String... args) throws Exception {
    logger.info("start process message.");
    while (true) {
      Message message = connection.receive();
      if (message != null) {
        String topic = message.getTopic();//从这里可以获得deviceID
        boolean handlerResult = deviceMessageHandler.handler(topic, new String(message.getPayload()));
        if (handlerResult) {
          message.ack();
        }
      }
    }
  }
}
