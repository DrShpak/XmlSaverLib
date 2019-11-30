package xmlSaver;

import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class XmlDeserializer {
    private static final Map<String,Class> builtInMap = new HashMap<>();

    public static Object loadXml(String path) {
        var xmlReader = new XmlNodeReader(path);
        var xmlObject = xmlReader.load();
        return loadAtomic(xmlObject);
    }

    private static Object loadAtomic(XmlNode xmlDescription) {
        var actualType = xmlDescription.hasAttribute("class") ? getClassInformation(xmlDescription) : null;
        if (actualType == null) {
            return null;
        }
        if (isNull(actualType)) {
            return loadNull(actualType, xmlDescription);
        }
        if (ClassUtils.isPrimitiveOrWrapper(actualType) || actualType == String.class) {
            return loadPrimitive(actualType, xmlDescription);
        } else if (actualType.isEnum()) {
            return loadEnum(actualType, xmlDescription);
        } else if (actualType.isArray()) {
            return loadArray(actualType, xmlDescription);
        } else if (Collection.class.isAssignableFrom(actualType)) {
            return loadCollection(actualType, xmlDescription);
        } else if (Map.class.isAssignableFrom(actualType)) {
            return loadMap(actualType, xmlDescription);
        } else {
            return loadObject(actualType, xmlDescription);
        }
    }

    @SuppressWarnings("SameReturnValue")
    private static Object loadNull(
            @SuppressWarnings("unused") Class<?> clazz,
            @SuppressWarnings("unused") XmlNode xmlDescription
    ) {
        return null;
    }

    private static Object loadPrimitive(Class<?> clazz, XmlNode xmlDescription) {
        var value = xmlDescription.getNodeValue();
        if(Boolean.class == clazz || Boolean.TYPE == clazz) return Boolean.parseBoolean(value);
        if(Byte.class == clazz || Byte.TYPE == clazz) return Byte.parseByte(value);
        if(Short.class == clazz || Short.TYPE == clazz) return Short.parseShort(value);
        if(Integer.class == clazz || Integer.TYPE == clazz) return Integer.parseInt(value);
        if(Long.class == clazz || Long.TYPE == clazz) return Long.parseLong(value);
        if(Float.class == clazz || Float.TYPE == clazz) return Float.parseFloat(value);
        if(Double.class == clazz || Double.TYPE == clazz) return Double.parseDouble(value);
        return value;
    }

    private static Object loadEnum(Class<?> clazz, XmlNode xmlDescription) {
        try {
            return clazz.
                    getMethod("valueOf", String.class).
                    invoke(null, xmlDescription.getNodeValue());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object loadArray(Class<?> clazz, XmlNode xmlDescription) {
        var items = xmlDescription.getChildNodes("item");
        var c_type = clazz.getComponentType();
        var value = Array.newInstance(c_type, items.length);
        for (int i = 0; i < items.length; i++) {
            Array.set(value, i, loadAtomic(items[i]));
        }
        return value;
    }

    private static Object loadCollection(Class<?> clazz, XmlNode xmlDescription) {
        try {
            var value = (Collection)clazz.getConstructor().newInstance();
            //noinspection unchecked
            Arrays.stream(xmlDescription.getChildNodes("item")).
                    forEach(x -> value.add(loadAtomic(x)));
            return value;
        } catch (Exception e) {
           throw new IllegalStateException(e);
        }
    }

    private static Object loadMap(Class<?> clazz, XmlNode xmlDescription) {
        try {
            var value = (Map)clazz.getConstructor().newInstance();
            Arrays.stream(xmlDescription.getChildNodes("item")).
                    forEach(x -> {
                        var key = x.getChildNode("key");
                        var val = x.getChildNode("value");
                        //noinspection unchecked
                        value.put(
                                loadAtomic(key),
                                loadAtomic(val)
                        );
                    });
            return value;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object loadObject(Class<?> clazz, XmlNode xmlDescription) {
        if (!clazz.isAnnotationPresent(XML.class)) {
            throw new IllegalStateException(clazz + " isn`t annotated with @xml.XML");
        }

        try {
            var object = clazz.getConstructor().newInstance();
            var loadableFields = Arrays.stream(XmlSerializer.collectFields(clazz)).
                    filter(x -> x.isAnnotationPresent(XML.class)).
                    collect(Collectors.toList());
            loadableFields.forEach(x -> loadField(object, x, xmlDescription));
            return object;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void loadField(Object target, Field field, XmlNode parent) {
        var xmlDescription = parent.getChildNode(field.getName());
        var fieldValue = loadAtomic(xmlDescription);
        setFieldValue(target, fieldValue, field);
    }

    private static Class<?> getClassInformation(XmlNode xmlDescription) {
        try {
            var clazzName = xmlDescription.getAttribute("class");
            var clazzType = builtInMap.containsKey(clazzName) ? builtInMap.get(clazzName) : Class.forName(clazzName);
            return obtainArrayClass(clazzType,
                    xmlDescription.hasAttribute("dimension") ?
                    Integer.parseInt(xmlDescription.getAttribute("dimension")) :
                    0
           );
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isNull(Class<?> clazz) {
        return (Void.class == clazz || Void.TYPE == clazz);
    }

    private static Class<?> obtainArrayClass(Class<?> c_type, int dimension) {
        if (dimension < 1) {
            return c_type;
        } else {
            return obtainArrayClass(Array.newInstance(c_type, 0).getClass(), dimension - 1);
        }
    }

    private static synchronized void setFieldValue(Object target, Object value, Field field) {
        try {
            var oldAccessibleState = field.canAccess(target);
            var oldModifiers = field.getModifiers();
            var modifiers = Field.class.getDeclaredField("modifiers");

            modifiers.setAccessible(true);
            modifiers.setInt(field, oldModifiers & ~Modifier.FINAL);
            field.setAccessible(true);
            field.set(target, value);
            field.setAccessible(oldAccessibleState);
            modifiers.setInt(field, oldModifiers);
            modifiers.setAccessible(false);
        } catch (Exception e) {
            throw new IllegalStateException("wtf");
        }
    }

    static {
        builtInMap.put("int", Integer.TYPE);
        builtInMap.put("long", Long.TYPE);
        builtInMap.put("double", Double.TYPE);
        builtInMap.put("float", Float.TYPE);
        builtInMap.put("bool", Boolean.TYPE);
        builtInMap.put("char", Character.TYPE);
        builtInMap.put("byte", Byte.TYPE);
        builtInMap.put("void", Void.TYPE);
        builtInMap.put("short", Short.TYPE);
    }
}
