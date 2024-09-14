package io.github.areebgillani.boost.pojos;

import java.lang.reflect.Method;

public class EndPointController {
    String endPoint;
    String controllerName;
    Method instanceMethod;

    public EndPointController(String endPoint, String controllerName, Method instanceMethod) {
        this.endPoint = endPoint;
        this.controllerName = controllerName;
        this.instanceMethod = instanceMethod;
    }

    public String getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(String endPoint) {
        this.endPoint = endPoint;
    }

    public String getControllerName() {
        return controllerName;
    }

    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }

    public Method getInstanceMethod() {
        return instanceMethod;
    }

    public void setInstanceMethod(Method instanceMethod) {
        this.instanceMethod = instanceMethod;
    }
}
