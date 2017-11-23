/*
 * The MIT License
 *
 * Copyright 2014 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.apache.commons.lang.ClassUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;

/**
 * A whitelist based on listing signatures and searching them.
 */
public abstract class EnumeratingWhitelist extends Whitelist {

    protected abstract List<MethodSignature> methodSignatures();

    protected abstract List<NewSignature> newSignatures();

    protected abstract List<MethodSignature> staticMethodSignatures();

    protected abstract List<FieldSignature> fieldSignatures();

    protected abstract List<FieldSignature> staticFieldSignatures();

    // TODO should precompute hash sets of signatures, assuming we document that the signatures may not change over the lifetime of the whitelist (or pass them in the constructor)

    @Override public final boolean permitsMethod(Method method, Object receiver, Object[] args) {
        for (MethodSignature s : methodSignatures()) {
            if (s.matches(method)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsConstructor(Constructor<?> constructor, Object[] args) {
        for (NewSignature s : newSignatures()) {
            if (s.matches(constructor)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsStaticMethod(Method method, Object[] args) {
        for (MethodSignature s : staticMethodSignatures()) {
            if (s.matches(method)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsFieldGet(Field field, Object receiver) {
        for (FieldSignature s : fieldSignatures()) {
            if (s.matches(field)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsFieldSet(Field field, Object receiver, Object value) {
        for (FieldSignature s : fieldSignatures()) {
            if (s.matches(field)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsStaticFieldGet(Field field) {
        for (FieldSignature s : staticFieldSignatures()) {
            if (s.matches(field)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsStaticFieldSet(Field field, Object value) {
        for (FieldSignature s : staticFieldSignatures()) {
            if (s.matches(field)) {
                return true;
            }
        }
        return false;
    }

    public static @Nonnull String getName(@Nonnull Class<?> c) {
        Class<?> e = c.getComponentType();
        if (e == null) {
            return c.getName();
        } else {
            return getName(e) + "[]";
        }
    }

    public static @Nonnull String getName(@CheckForNull Object o) {
        return o == null ? "null" : getName(o.getClass());
    }

    private static String[] argumentTypes(Class<?>[] argumentTypes) {
        String[] s = new String[argumentTypes.length];
        for (int i = 0; i < argumentTypes.length; i++) {
            s[i] = getName(argumentTypes[i]);
        }
        return s;
    }

    private static boolean is(String thisIdentifier, String identifier) {
        return isWildCard(thisIdentifier) || identifier.equals(thisIdentifier);
    }

    private static boolean isWildCard(String identifier) {
        return "*".equals(identifier);
    }


    public static abstract class Signature implements Comparable<Signature> {
        String cachedString = null;

        /** Return the cachedString String representation of the signature.
         *  This avoids redundant String work and reduces hash comparison time, since Strings keep their hashes.
         */
        public String getCachedString() {
            String output = cachedString;
            if (output == null) {
                output = toString();
                cachedString = output;
            }
            return output;
        }

        /** Form as in {@link StaticWhitelist} entries. */
        @Override public abstract String toString();
        static StringBuilder joinWithSpaces(StringBuilder b, String[] types) {
            for (String type : types) {
                b.append(' ').append(type);
            }
            return b;
        }
        abstract String signaturePart();
        @Override public int compareTo(Signature o) {
            int r = signaturePart().compareTo(o.signaturePart());
            return r != 0 ? r : toString().compareTo(o.toString());
        }
        @Override public boolean equals(Object obj) {
            return obj != null && obj.getClass() == getClass() && getCachedString().equals(((Signature)obj).getCachedString());
        }
        @Override public int hashCode() {
            return getCachedString().hashCode();  // Strings store precomputed hash
        }
        abstract boolean exists() throws Exception;
        final Class<?> type(String name) throws Exception {
            return ClassUtils.getClass(name);
        }
        final Class<?>[] types(String[] names) throws Exception {
            Class<?>[] r = new Class<?>[names.length];
            for (int i = 0; i < names.length; i++) {
                r[i] = type(names[i]);
            }
            return r;
        }
    }

    public static class MethodSignature extends Signature {
        final String receiverType, method;
        final String[] argumentTypes;
        public MethodSignature(String receiverType, String method, String[] argumentTypes) {
            this.receiverType = receiverType;
            this.method = method;
            this.argumentTypes = argumentTypes.clone();
        }
        public MethodSignature(Class<?> receiverType, String method, Class<?>... argumentTypes) {
            this(getName(receiverType), method, argumentTypes(argumentTypes));
        }

        String canonicalForm(Method m) {
            return Signature.joinWithSpaces(new StringBuilder(getName(m.getDeclaringClass())).append(' ').append(m.getName()), argumentTypes(m.getParameterTypes()).toString();
        }

        boolean matches(Method m) {
            return is(method, m.getName()) && getName(m.getDeclaringClass()).equals(receiverType) && Arrays.equals(argumentTypes(m.getParameterTypes()), argumentTypes);
        }
        @Override public String toString() {
            return "method " + signaturePart();
        }
        @Override String signaturePart() {
            return joinWithSpaces(new StringBuilder(receiverType).append(' ').append(method), argumentTypes).toString();
        }
        @Override boolean exists() throws Exception {
            return exists(type(receiverType), true);
        }
        // Cf. GroovyCallSiteSelector.visitTypes.
        @SuppressWarnings("InfiniteRecursion")
        private boolean exists(Class<?> c, boolean start) throws Exception {
            Class<?> s = c.getSuperclass();
            if (s != null && exists(s, false)) {
                return !start;
            }
            for (Class<?> i : c.getInterfaces()) {
                if (exists(i, false)) {
                    return !start;
                }
            }
            try {
                return !Modifier.isStatic(c.getDeclaredMethod(method, types(argumentTypes)).getModifiers());
            } catch (NoSuchMethodException x) {
                return false;
            }
        }
    }

    static class StaticMethodSignature extends MethodSignature {
        StaticMethodSignature(String receiverType, String method, String[] argumentTypes) {
            super(receiverType, method, argumentTypes);
        }
        @Override public String toString() {
            return "staticMethod " + signaturePart();
        }
        @Override boolean exists() throws Exception {
            try {
                return Modifier.isStatic(type(receiverType).getDeclaredMethod(method, types(argumentTypes)).getModifiers());
            } catch (NoSuchMethodException x) {
                return false;
            }
        }
    }

    public static final class NewSignature extends Signature  {
        private final String type;
        private final String[] argumentTypes;
        public NewSignature(String type, String[] argumentTypes) {
            this.type = type;
            this.argumentTypes = argumentTypes.clone();
        }
        public NewSignature(Class<?> type, Class<?>... argumentTypes) {
            this(getName(type), argumentTypes(argumentTypes));
        }

        static String canonicalize(Constructor c) {

        }

        boolean matches(Constructor c) {
            return getName(c.getDeclaringClass()).equals(type) && Arrays.equals(argumentTypes(c.getParameterTypes()), argumentTypes);
        }
        @Override String signaturePart() {
            return joinWithSpaces(new StringBuilder(type), argumentTypes).toString();
        }
        @Override public String toString() {
            return "new " + signaturePart();
        }
        @Override boolean exists() throws Exception {
            try {
                type(type).getDeclaredConstructor(types(argumentTypes));
                return true;
            } catch (NoSuchMethodException x) {
                return false;
            }
        }
    }

    public static String canonicalField(Field f) {

    }

    public static class FieldSignature extends Signature {
        final String type, field;
        public FieldSignature(String type, String field) {
            this.type = type;
            this.field = field;
        }
        public FieldSignature(Class<?> type, String field) {
            this(getName(type), field);
        }
        boolean matches(Field f) {
            return is(field, f.getName()) && getName(f.getDeclaringClass()).equals(type);
        }

        public String canonicalForm(Field f) {
            return "field "+getName(f.getDeclaringClass()) + ' ' + f.getName();
        }

        @Override String signaturePart() {
            return type + ' ' + field;
        }
        @Override public String toString() {
            return "field " + signaturePart();
        }
        @Override boolean exists() throws Exception {
            try {
                type(type).getField(field);
                return true;
            } catch (NoSuchFieldException x) {
                return false;
            }
        }
    }

    static class StaticFieldSignature extends FieldSignature {
        StaticFieldSignature(String type, String field) {
            super(type, field);
        }
        @Override public String toString() {
            return "staticField " + signaturePart();
        }
    }

}
