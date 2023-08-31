# vertx-boost
This project adds the flavor of SpringBoot's style annotations in vertx to reduce the learning curve. It follows the same annotation style such as @RestController, @Service, @Autowired, @RequestParam, @RequestBody, @PostMapping &amp; @GetMapping whereas controller and service classes should extend AbstractVerticle as per the requirement of Vertx.

# Background & Basics

In Vertx a router needs to be decalared in order to register API endpoints in the application. Each route has its own handler which entertains the logic once API endpoint is called. It becomes very hard to maintain so many route handlers and like most people tend to declare all the routes in the same class then it's a very hard class to maintain.
Vertx says that every class which extends abstractverticle will be handled by their own dedicated threads and threadpools and will have its own life cycle. I am asuming that you have the basic idea of MainVerticle and WorkerVerticle. If you don't have the idea then please visit https://vertx.io first.
A service is basically a worker verticle and you can configure it using the following json. 
# Config
{
"workerPoolName": "testWorker",
"workerPoolSize": 10,
"workerInstance": 2
}

# Usage

Booster which is the initializing class of this utility requires this JsonObject in the constructor in order to initialize. 
# Code
new Booster(vertx, router, jsonObject).boost("[base package to scan for all the above mentioned annotations]");

# Note
@Autowired will not work in controller classes because all the controllers runs on eventloops and one can't block the eventloop's thread. Vertx will throw exception if eventloop thread is blocked. That is why composition is prohibited.
