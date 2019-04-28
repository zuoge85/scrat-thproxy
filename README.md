# scrat-thproxy 

一个中转代理http请求到本地的客户端项目,类似ngrok但是有一些不一样。 

主要解决 微信支付回调，支付宝支付回调，等接口的调试

1. 支持注册大量本地客户端 

2. 支持单元测试等代码集成

3. 完全开源

4. 完全基于spring webflux高性能

！半成品已经开始在项目单元测试使用，哈哈

## 项目结构

1. cli 表示命令行客户端，未完工
2. client 客户端项目, 客户端基础库，可以单元测试集成 
3. core 基础模块
4. server 服务器模块


## 使用技术

spring webflux

## 流程


server 收到http 请求后通过websocket 转发到本地 client

本地客户端可以转发到本地服务器

单元测试可以通过 client 库直接处理




