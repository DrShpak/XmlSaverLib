package xmlSaver;

import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class XmlSerializer {
    public static void saveXml(Object object, String path) {
        var xmlObject = new XmlNode("root");
        saveAtomic(object, xmlObject);

        var xmlWriter = new XmlNodeWriter(path);
        xmlWriter.save(xmlObject);
    }

    private static void saveAtomic(Object target, XmlNode xmlDescription) {
        if (target == null) {
            saveNull(xmlDescription);
            return;
        }
        var objectClazz = target.getClass();
        if (ClassUtils.isPrimitiveOrWrapper(objectClazz) || objectClazz == String.class) {
            savePrimitive(target, xmlDescription);
        } else if (objectClazz.isEnum()) {
            saveEnum(target, xmlDescription);
        } else if (objectClazz.isArray()) {
            saveArray(target, xmlDescription);
        } else if (Collection.class.isAssignableFrom(objectClazz)) {
            saveCollection(target, xmlDescription);
        } else if (Map.class.isAssignableFrom(objectClazz)) {
            saveMap(target, xmlDescription);
        } else {
            saveObject(target, xmlDescription);
        }
    }

    private static void saveNull(XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", Void.TYPE.getCanonicalName());
        xmlDescription.setValue("null");
    }

    private static void savePrimitive(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", target.getClass().getCanonicalName());
        xmlDescription.setValue(target.toString());
    }

    private static void saveEnum(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", target.getClass().getCanonicalName());
        xmlDescription.setValue(((Enum<?>)target).name());
    }

    private static void saveArray(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", getArrayCType(target.getClass()).getCanonicalName());
        xmlDescription.appendAttribute("dimension", String.valueOf(getArrayDimension(target.getClass())));
        for (Object e : getObjectArraySafe(target)) {
            saveAtomic(e, new XmlNode(
                    "item",
                    xmlDescription
            ));
        }
    }

    private static void saveCollection(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", target.getClass().getCanonicalName());
        ((Collection<?>)target).
                forEach(x -> saveAtomic(x, new XmlNode(
                            "item",
                                xmlDescription,
                                Map.entry("class", x.getClass().getCanonicalName())
                            )
                        )
                );
    }

    private static void saveMap(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", target.getClass().getCanonicalName());
        ((Map<?, ?>)target).forEach((k, v) -> {
                    var itemXmlDescription = new XmlNode("item", xmlDescription);
                    var keyXmlDescription = new XmlNode(
                            "key",
                            itemXmlDescription,
                            Map.entry("class", k.getClass().getCanonicalName())
                    );
                    var valueXmlDescription = new XmlNode(
                            "value",
                            itemXmlDescription,
                            Map.entry("class", k.getClass().getCanonicalName())
                    );
                    saveAtomic(k, keyXmlDescription);
                    saveAtomic(v, valueXmlDescription);
                }
        );
    }

    private static void saveObject(Object object, XmlNode xmlDescription) {
        var clazz = object.getClass();
        if (!clazz.isAnnotationPresent(XML.class)) {
            throw new IllegalStateException(object.getClass() + " isn`t annotated with @xml.XML");
        }
        xmlDescription.appendAttribute("class", clazz.getCanonicalName());
        var savableFields = Arrays.stream(collectFields(clazz)).
                filter(x -> x.isAnnotationPresent(XML.class)).
                collect(Collectors.toList());
        savableFields.forEach(x -> saveField(object, x, xmlDescription));
    }

    private static void saveField(Object target, Field field, XmlNode parent) {
        var xmlDescription = new XmlNode(field.getName(), parent);
        var fieldValue = getFieldValue(target, field);
        saveAtomic(fieldValue, xmlDescription);
    }

    static Field[] collectFields(Class<?> clazz) {
        var fields = new ArrayList<Field>();
        collectFields(clazz, fields);
        return fields.toArray(Field[]::new);
    }

    private static void collectFields(Class<?> clazz, ArrayList<Field> fields) {
        if (clazz == Object.class) {
            return;
        }
        fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
        collectFields(clazz.getSuperclass(), fields);
    }

    private static Class<?> getArrayCType(Class<?> arrayClazz) {
        while (arrayClazz.isArray()) {
            arrayClazz = arrayClazz.getComponentType();
        }
        return arrayClazz;
    }

    private static int getArrayDimension(Class<?> arrayClazz) {
        int count = 0;
        while (arrayClazz.isArray()) {
            count++;
            arrayClazz = arrayClazz.getComponentType();
        }
        return count;
    }

    private static Object[] getObjectArraySafe(Object array) {
        var arrLen = Array.getLength(array);
        var safeArr = new Object[arrLen];
        for (int i = 0; i < arrLen; i++) {
            safeArr[i] = Array.get(array, i);
        }
        return safeArr;
    }

    private static synchronized Object getFieldValue(Object target, Field field) {
        var oldAccessibleState = field.canAccess(target);
        field.setAccessible(true);
        Object value;
        try {
            value = field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("wtf");
        }
        field.setAccessible(oldAccessibleState);
        return value;
    }
}
