package xmlSaver;

import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

public class XmlSerializer {
    private XmlSerializerRegistry registry;

    public static void saveXml(Object object, String path, XmlSerializerRegistry registry) {
        var serializer = new XmlSerializer();
        serializer.registry = registry;
        serializer.saveXmlInternal(object, path);
    }

    public static void saveXml(Object object, String path) {
        new XmlSerializer().saveXmlInternal(object, path);
    }

    private final HashMap<Object, String> trackingObjects = new HashMap<>();

    private void saveXmlInternal(Object object, String path) {
        var xmlObject = new XmlNode("root");
        saveAtomic(object, xmlObject);

        var xmlWriter = new XmlNodeWriter(path);
        xmlWriter.save(xmlObject);
    }

    private void saveAtomic(Object target, XmlNode xmlDescription) {
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

    private void saveNull(XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", Void.TYPE.getCanonicalName());
        xmlDescription.setValue("null");
    }

    private void savePrimitive(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", target.getClass().getCanonicalName());
        xmlDescription.setValue(target.toString());
    }

    private void saveEnum(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", target.getClass().getCanonicalName());
        xmlDescription.setValue(((Enum<?>) target).name());
    }

    private void saveArray(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", getArrayCType(target.getClass()).getCanonicalName());
        xmlDescription.appendAttribute("dimension", String.valueOf(getArrayDimension(target.getClass())));
        for (Object e : getObjectArraySafe(target)) {
            saveAtomic(e, new XmlNode(
                "item",
                xmlDescription
            ));
        }
    }

    private void saveCollection(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", target.getClass().getCanonicalName());
        ((Collection<?>) target).
            forEach(x -> saveAtomic(x, new XmlNode(
                    "item",
                    xmlDescription
                )
                )
            );
    }

    private void saveMap(Object target, XmlNode xmlDescription) {
        xmlDescription.appendAttribute("class", target.getClass().getCanonicalName());
        ((Map<?, ?>) target).forEach((k, v) -> {
                var itemXmlDescription = new XmlNode("item", xmlDescription);
                var keyXmlDescription = new XmlNode(
                    "key",
                    itemXmlDescription
                );
                var valueXmlDescription = new XmlNode(
                    "value",
                    itemXmlDescription
                );
                saveAtomic(k, keyXmlDescription);
                saveAtomic(v, valueXmlDescription);
            }
        );
    }

    private void saveObject(Object object, XmlNode xmlDescription) {
        var clazz = object.getClass();
        XmlSerializerRegistry.XmlSerializationStrategy strategy = null;
        if (this.registry != null) {
            strategy = this.registry.getClassStrategy(clazz);
        }
        if (!clazz.isAnnotationPresent(XML.class) && strategy == null) {
            throw new IllegalStateException(object.getClass() + " isn`t annotated with @xml.XML");
        }
        if (this.isTracking(object)) {
            xmlDescription.appendAttribute("objectId", this.getObjIdentity(object));
            return;
        } else {
            this.trackObject(object);
            xmlDescription.appendAttribute("objectId", this.getObjIdentity(object));
        }
        xmlDescription.appendAttribute("class", clazz.getCanonicalName());
        var savableFields = strategy != null ? strategy.getFields() : Arrays.stream(collectFields(clazz)).
            filter(x -> x.isAnnotationPresent(XML.class)).
            toArray(Field[]::new);
        Arrays.stream(savableFields).forEach(x -> saveField(object, x, xmlDescription));
    }

    private void saveField(Object target, Field field, XmlNode parent) {
        var xmlDescription = new XmlNode(field.getName(), parent);
        var fieldValue = getFieldValue(target, field);
        saveAtomic(fieldValue, xmlDescription);
    }

    private void trackObject(Object object) {
        if (!this.isTracking(object)) {
            this.trackingObjects.put(
                object,
                String.valueOf(1000 + this.trackingObjects.size())
            );
        }
    }

    private boolean isTracking(Object object) {
        return this.getObjIdentity(object) != null;
    }

    private String getObjIdentity(Object object) {
        return this.trackingObjects.
            entrySet().
            stream().
            filter(x -> object.getClass().getAnnotation(XML.class).isStrict() ? x.getKey() == object : x.getKey().equals(object)).
            map(Map.Entry::getValue).
            findAny().
            orElse(null);
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
