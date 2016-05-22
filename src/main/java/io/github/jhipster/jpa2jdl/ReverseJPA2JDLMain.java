package io.github.jhipster.jpa2jdl;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.javadude.scannit.Configuration;
import nl.javadude.scannit.Scannit;
import nl.javadude.scannit.scanner.SubTypeScanner;
import nl.javadude.scannit.scanner.TypeAnnotationScanner;

public class ReverseJPA2JDLMain {
    
    private static final Logger LOG = LoggerFactory.getLogger(ReverseJPA2JDLMain.class);
    
    @Option(name="--packageName")
    private String packageName;
    
    public static void main(String[] args) {
        ReverseJPA2JDLMain app = new ReverseJPA2JDLMain();
        CmdLineParser parser = new CmdLineParser(app);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            // handling of wrong arguments
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        }
        app.run();
    }
    
    @SuppressWarnings("unchecked")
    public void run() {
	Configuration config = Configuration.config()
		    .with(new SubTypeScanner(), new TypeAnnotationScanner())
		    .scan(packageName);
	Scannit scannit = new Scannit(config);
	Set<Class<?>> entityClasses = scannit.getTypesAnnotatedWith(Entity.class);
	Set<Class<?>> entitySubClasses = new HashSet<>();
	entitySubClasses.addAll(entityClasses);
	for(Class<?> e : entityClasses) {
	    Set<Class<?>> subClasses = (Set<Class<?>>) (Set<?>) scannit.getSubTypesOf(e);
	    if (subClasses != null && !subClasses.isEmpty()) {
		entitySubClasses.addAll(subClasses);
	    }
	}
	
	LOG.info("Found @Entity classes:" + entityClasses.size() + " : " + entityClasses);
	if (entitySubClasses.size() > entityClasses.size()) {
	    LOG.info("Found sub-classes of @Entity classes:" + entitySubClasses.size() + " : " + entitySubClasses);
	}

	// scan enum types in entities field type
	Set<Class<?>> enums = new LinkedHashSet<>();
	for(Class<?> e : entitySubClasses) {
	    Field[] declaredFields = e.getDeclaredFields();
	    for(Field f : declaredFields) {
		Class<?> fieldType = f.getType();
		if (fieldType.isEnum()) {
		    if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
    		    	continue;
    		    }
		    enums.add(fieldType);
		}
	    }
	}
	
	
	// ** generate **
	StringBuilder jdl = new StringBuilder();

	for(Class<?> e : enums) {
	    generateEnum2Jdl(jdl, e);
	}
	
	StringBuilder relationShips = new StringBuilder();
	for(Class<?> e : entitySubClasses) {
	    generateClass2Jdl(jdl, relationShips, e);
	}
	jdl.append(relationShips);
	
	for(Class<?> e : entitySubClasses) {
	    generatePagination(jdl, e);
	}
	for(Class<?> e : entitySubClasses) {
	    generateDtoServices(jdl, e);
	}
	
	System.out.println(jdl);
    }

    private void generatePagination(StringBuilder out, Class<?> e) {
	String entityClassName = e.getSimpleName();
	out.append("paginate " + entityClassName + " with pagination\n");
	out.append("\n");
    }
    
    private void generateDtoServices(StringBuilder out, Class<?> e) {
	String entityClassName = e.getSimpleName();
	out.append("dto " + entityClassName + " with mapstruct\n");
	out.append("service " + entityClassName + " with serviceClass\n");
	out.append("\n");
    }
    
    
    protected void generateEnum2Jdl(StringBuilder out, Class<?> e) {
	String entityClassName = e.getSimpleName();
	boolean firstField = true;
	out.append("enum " + entityClassName + " {\n");
	Field[] declaredFields = e.getDeclaredFields();
	for(Field f : declaredFields) {
	    String fieldName = f.getName();
	    if (f.isSynthetic() || !Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers())) {
		continue;
	    }
	    if (firstField) {
		firstField = false;
	    } else {
		out.append(",\n");
	    }
	    out.append("  " + fieldName);
	}

	out.append("\n");
	out.append("}\n\n");
    }
    
    protected void generateClass2Jdl(StringBuilder out, StringBuilder relationShips,  Class<?> e) {
	String entityClassName = e.getSimpleName();
	boolean firstField = true;
	out.append("entity " + entityClassName + " {\n"); // inheritance NOT SUPPORTED YET in JDL ???
	
	Field[] declaredFields = e.getDeclaredFields();
	for(Field f : declaredFields) {
	    String fieldName = f.getName();
	    Type fieldType = f.getType();
	    if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
		continue;
	    }
	    // Annotation[] fieldAnnotations = f.getDeclaredAnnotations();
	    
	    if (f.getDeclaredAnnotation(Transient.class) != null) {
		continue;
	    }
	    
	    String relationType = null;
	    Class<?> targetEntityClass = null;
	    boolean fromMany = false;
	    boolean toMany = false;
	    String mappedBy = "";
	    OneToMany oneToManyAnnotation = f.getDeclaredAnnotation(OneToMany.class);
	    if (oneToManyAnnotation != null) {
		relationType = "OneToMany";
		targetEntityClass = oneToManyAnnotation.targetEntity();
		fromMany = false;
		toMany = true;
		mappedBy = oneToManyAnnotation.mappedBy();
	    }
	    OneToOne oneToOneAnnotation = f.getDeclaredAnnotation(OneToOne.class);
	    if (oneToOneAnnotation != null) {
		relationType = "OneToOne";
		targetEntityClass = oneToOneAnnotation.targetEntity();
		fromMany = false;
		toMany = false;
		mappedBy = oneToOneAnnotation.mappedBy();
	    }
	    ManyToMany manyToManyAnnotation = f.getDeclaredAnnotation(ManyToMany.class);
	    if (manyToManyAnnotation != null) {
		relationType = "ManyToMany";
		targetEntityClass = manyToManyAnnotation.targetEntity();
		fromMany = true;
		toMany = true;
		mappedBy = manyToManyAnnotation.mappedBy();
	    }
	    ManyToOne manyToOneAnnotation = f.getDeclaredAnnotation(ManyToOne.class);
	    if (manyToOneAnnotation != null) {
		relationType = "ManyToOne";
		targetEntityClass = manyToOneAnnotation.targetEntity();
		fromMany = true;
		toMany = false;
	    }
	    
	    if (relationType != null) {
		// relationship
		relationShips.append("relationship " + relationType + " {\n");

		if (targetEntityClass == void.class || targetEntityClass == null) {
		    targetEntityClass = typeToClass(fieldType);
		}
		
		if (toMany && targetEntityClass != null) {
		    if (Collection.class.isAssignableFrom(targetEntityClass)) {
			Class<?> compType = targetEntityClass.getComponentType();
			if (compType != null) {
			    targetEntityClass = compType;
			} else {
			    Type fieldGenericType = f.getGenericType();
			    if (fieldGenericType instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) fieldGenericType;
				targetEntityClass = typeToClass(pt.getActualTypeArguments()[0]);
			    }
			}
		    }
		}
	
		String targetEntityClassName = targetEntityClass != null? targetEntityClass.getSimpleName() : "";
		
		if (fromMany && toMany
			// fieldName.equals("")
			) {
		    LOG.info("ManyToMany .. mappedBy ??");
		}
		relationShips.append("  " + entityClassName + "{" + fieldName);
		if (mappedBy != null && !"".equals(mappedBy)) {
		    relationShips.append("(" + mappedBy + ")");
		}
		relationShips.append("} to " + targetEntityClassName + "\n");
		
		relationShips.append("}\n\n");
	    } else {
		// simple field
		if (firstField) {
		    firstField = false;
		} else {
		    out.append(",\n");
		}
		out.append("  " + fieldName + " " + f.getType().getSimpleName());
	    }
	    
	}

	out.append("\n");
	out.append("}\n\n");
    }
    
    
    
    /**
     * Get the underlying class for a type, or null if the type is a variable type.
     *
     * @param type the type
     * @return the underlying class
     */
    public static Class<?> typeToClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return typeToClass(((ParameterizedType) type).getRawType());
        } else if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            Class<?> componentClass = typeToClass(componentType);
            if (componentClass != null) {
                return Array.newInstance(componentClass, 0).getClass();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Get the actual type arguments a child class has used to extend a generic base class.
     *
     * @param baseClass the base class
     * @param childClass the child class
     * @return a list of the raw classes for the actual type arguments.
     */
    public static <T> List<Class<?>> getTypeArguments(Class<T> baseClass, Class<? extends T> childClass) {
        Map<Type, Type> resolvedTypes = new HashMap<Type, Type>();
        Type type = childClass;
        // start walking up the inheritance hierarchy until we hit baseClass
        while (!typeToClass(type).equals(baseClass)) {
            if (type instanceof Class) {
                // there is no useful information for us in raw types, so just keep going.
                type = ((Class<?>) type).getGenericSuperclass();
            } else {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                Class<?> rawType = (Class<?>) parameterizedType.getRawType();

                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                TypeVariable<?>[] typeParameters = rawType.getTypeParameters();
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    resolvedTypes.put(typeParameters[i], actualTypeArguments[i]);
                }

                if (!rawType.equals(baseClass)) {
                    type = rawType.getGenericSuperclass();
                }
            }
        }

        // finally, for each actual type argument provided to baseClass, determine (if possible)
        // the raw class for that type argument.
        Type[] actualTypeArguments;
        if (type instanceof Class) {
            actualTypeArguments = ((Class<?>) type).getTypeParameters();
        } else {
            actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
        }
        List<Class<?>> typeArgumentsAsClasses = new ArrayList<Class<?>>();
        // resolve types by chasing down type variables.
        for (Type baseType : actualTypeArguments) {
            while (resolvedTypes.containsKey(baseType)) {
                baseType = resolvedTypes.get(baseType);
            }
            typeArgumentsAsClasses.add(typeToClass(baseType));
        }
        return typeArgumentsAsClasses;
    }

}
