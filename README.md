# vertx-boost
This project adds the flavor of SpringBoot's style annotations in vertx to reduce the learning curve. It follows the same annotation style such as @RestController, @Service, @Autowired, @RequestParam, @RequestBody, @PostMapping &amp; @GetMapping whereas controller and service classes should extend AbstractVerticle as per the requirement of Vertx.

# Background & Basics
### Vertx Vs Spring
Vertx is an event driven toolkit backed by eclipse foundation. It's a polyglot and is used for highly concurrent code writing. When compared to Spring Webflux or Boot vertx is exceptionally fast. In my performance testing I have found vertx 75% faster than spring. Techempower has also shared very similar results on their site as well https://www.techempower.com/benchmarks/#section=data-r21. Now considering this, if you want to develop a state of the art application with high throughput, one should go for vertx as it is Java's fastest available unopinionated framework available today (Techempower's results also backs this statement). 

### Basics of Vertx
In Vertx a router needs to be decalared in order to register API endpoints in the application. Each route has its own handler which entertains the logic once API endpoint is called. It becomes very hard to maintain so many route handlers and like most people tend to declare all the routes in the same class then it's a very hard class to maintain.

Vertx says that every class which extends abstractverticle will be handled by their own dedicated threads and threadpools and will have its own life cycle. I am asuming that you have the basic idea of MainVerticle and WorkerVerticle. If you don't have the idea then please visit https://vertx.io first. A service is basically a worker verticle and you can configure it using the following json. 
# Config
```json
{
  "workerPoolName": "testWorker",
  "workerPoolSize": 10,
  "workerInstance": 2
}
```

# Usage
Booster which is the initializing class of this utility requires this JsonObject in the constructor in order to initialize. 
### Add it in your root build.gradle at the end of repositories
```kotlin
allprojects {
		repositories {
			maven ("https://jitpack.io")
		}
	}
```
### Adding Dependency 

```kotlin
dependencies {
  implementation ("com.github.Areeb-Gillani:vertx-boost:0.0.1")
}
```
### Code
```java
new Booster(vertx, router, configJson).boost("[base package to scan for all the above mentioned annotations]");
```
### Note
@Autowired will not work in controller classes because all the controllers runs on eventloops and one can't block the eventloop's thread. Vertx will throw exception if eventloop thread is blocked. That is why composition is prohibited.

[![](https://jitpack.io/v/Areeb-Gillani/vertx-boost.svg)](https://jitpack.io/#Areeb-Gillani/vertx-boost)
