package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

public class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        return Class.forName(className, false, classLoader);
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            return findClass(className, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int getIntField(Object obj, String fieldName) {
        return (int) getObjectField(obj, fieldName);
    }

    public static void setIntField(Object obj, String fieldName, int value) {
        setObjectField(obj, fieldName, value);
    }

    public static long getLongField(Object obj, String fieldName) {
        return (long) getObjectField(obj, fieldName);
    }

    public static void setLongField(Object obj, String fieldName, long value) {
        setObjectField(obj, fieldName, value);
    }

    public static float getFloatField(Object obj, String fieldName) {
        return (float) getObjectField(obj, fieldName);
    }

    public static void setFloatField(Object obj, String fieldName, float value) {
        setObjectField(obj, fieldName, value);
    }

    public static double getDoubleField(Object obj, String fieldName) {
        return (double) getObjectField(obj, fieldName);
    }

    public static void setDoubleField(Object obj, String fieldName, double value) {
        setObjectField(obj, fieldName, value);
    }

    public static boolean getBooleanField(Object obj, String fieldName) {
        return (boolean) getObjectField(obj, fieldName);
    }

    public static void setBooleanField(Object obj, String fieldName, boolean value) {
        setObjectField(obj, fieldName, value);
    }

    public static Object callMethod(Object obj, String methodName, Object... args) throws Throwable {
        Class<?>[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        Method m = findMethodExact(obj.getClass(), methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(obj, args);
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) throws Throwable {
        Class<?>[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        Method m = findMethodExact(clazz, methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(null, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int getStaticIntField(Class<?> clazz, String fieldName) {
        return (int) getStaticObjectField(clazz, fieldName);
    }

    public static void setStaticIntField(Class<?> clazz, String fieldName, int value) {
        setStaticObjectField(clazz, fieldName, value);
    }

    public static float getStaticFloatField(Class<?> clazz, String fieldName) {
        return (float) getStaticObjectField(clazz, fieldName);
    }

    public static void setStaticFloatField(Class<?> clazz, String fieldName, float value) {
        setStaticObjectField(clazz, fieldName, value);
    }

    public static double getStaticDoubleField(Class<?> clazz, String fieldName) {
        return (double) getStaticObjectField(clazz, fieldName);
    }

    public static void setStaticDoubleField(Class<?> clazz, String fieldName, double value) {
        setStaticObjectField(clazz, fieldName, value);
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return clazz.getDeclaredMethod(methodName, parameterTypes);
    }

    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            return findMethodExact(clazz, methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>... parameterTypes) throws NoSuchMethodException {
        return clazz.getDeclaredConstructor(parameterTypes);
    }

    public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            return findConstructorExact(clazz, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setObjectSurrogateField(Object obj, String fieldName, Object value) {
        setObjectField(obj, fieldName, value);
    }

    public static Object getObjectSurrogateField(Object obj, String fieldName) {
        return getObjectField(obj, fieldName);
    }

    public static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return clazz.getDeclaredField(fieldName);
    }

    public static Field findFieldIfExists(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, XC_MethodHook callback, Object... parameterTypesAndCallback) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, XC_MethodHook callback, Object... parameterTypesAndCallback) {
        return null;
    }
}
