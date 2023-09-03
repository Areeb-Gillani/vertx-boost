[![](https://jitpack.io/v/Areeb-Gillani/vertx-boost.svg)](https://jitpack.io/#Areeb-Gillani/vertx-boost)
# vertx-boost
This project adds the flavor of SpringBoot's style annotations in vertx to reduce the learning curve. It follows the same annotation style such as @RestController, @Service, @Autowired, @RequestParam, @RequestBody, @PostMapping &amp; @GetMapping whereas controller and service classes should extend AbstractVerticle as per the implementation of Vertx.

# Background & Basics
### Vertx Vs Spring
Vertx is an event driven toolkit backed by eclipse foundation. It's a polyglot and is used for highly concurrent code writing. When compared to Spring Webflux or Boot vertx is exceptionally fast. In my performance testing I have found vertx 75% faster than spring. Techempower has also shared very similar results on their site as well https://www.techempower.com/benchmarks/#section=data-r21. Now considering this, if you want to develop a state of the art application with high throughput, one should go for vertx as it is Java's fastest available unopinionated framework available today (Techempower's results also backs this statement). 

### Basics of Vertx
In Vertx a router needs to be decalared in order to register API endpoints in the application. Each route has its own handler which entertains the logic once API endpoint is called. It becomes very hard to maintain so many route handlers and like most people tend to declare all the routes in the same class then it's a very hard class to maintain.

# Dependency
 Add it in your root build.gradle at the end of repositories
### Repository
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
  implementation ("com.github.Areeb-Gillani:vertx-boost:0.0.2")
}
```
Vertx says that every class which extends AbstractVerticle will be handled by their own dedicated threads and threadPools and will have its own life cycle. I am assuming that you have the basic idea of MainVerticle and WorkerVerticle. If you don't have the idea then please visit https://vertx.io first. A service is basically a worker verticle, you can configure it using the following json. 
# Config
```json
{
  "workers": {
    "ExampleWorker": {
      "instance": 5,
      "poolSize": 6
    }
  }
}
```

# Usage
Booster which is the initializing class of this utility requires this JsonObject in the constructor in order to initialize. 
### Initializer
Please initialize the booster class in your main application class to register everything on startup.
```java
public class Application {
    public void initVertxBoost(){
        Booster booster = new Booster(vertx, router, config);
        booster.boost(this.getClass().getPackage().getName());
    }
}
```
### Controller
```java
@RestController
public class ExampleController extends AbstractVerticle{
    @GetMapping("/sayHi")
    public String sayHi(){
        return "hi";
    }
    @GetMapping("/sayHello")
    public String sayHello(@RequestParam("username") String user){
        return "Hello "+user;
    }
    @PostMapping("/sayHiToUser")
    public String sayHiToUser(JsonObject body){
        return "Hi! " +body.getString("username");
    }
    @PostMapping("/replyHiToUser")
    public void replyHiToUser(JsonObject body, RoutingContext context){
        return vertx.eventBus().request("MyTopic", body, reply->{
            if(reply.succeeded()){
                context.json(reply.result().body());
            }
        });
    }
}
```
#### Note
- We can't call service function directly to keep our controller lightweight so if you want some blocking call use event bus to pass that to worker thread.
- Return in this case will be handled by vertx that is why controller's return type is void. We are writing the response directly to out routing context
- @Autowired will not work in controller classes because all the controllers runs on eventloops and one can't block the eventloop's thread. Vertx will throw exception if eventloop thread is blocked. That is why composition is prohibited.

### Service
```java
@Service("ExampleWorker") //It is the same name which is described in configuration
public class ExampleService extends AbstractVerticle{
    @Autowired
    DatabaseRepo myRepo;
    @Override
    public void start(){
        vertx.eventBus().consumer("MyTopic", this::replyHiToUser);
    }
    private void replyHiToUser(Message<Object> message){
        JsonObject vertxJsonObject = (JsonObject) message; 
        message.reply("Hi "+ vertxJsonObject.getString("username"));
    }
}
```
#### Note
- Service will never return anything directly instead it will use reply method to return the response.
- Bind all the methods to the topics in start method.

### Repository
```java
public class DatabaseRepo {
    //Write your db operations here 
}
```
#### Announcement
- JPA style repository system will be out soon.
