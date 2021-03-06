/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.jvm;

import java.util.*;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Filter;
import com.sun.tools.javac.util.Name;

/** An internal structure that corresponds to the constant pool of a classfile.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Pool {

    public static final int MAX_ENTRIES = 0xFFFF;
    public static final int MAX_STRING_LENGTH = 0xFFFF;

    /** Index of next constant to be entered.
     */
    int pp;

    /** The initial pool buffer.
     */
    Object[] pool;

    /** A hashtable containing all constants in the pool.
     */
    Map<Object,Integer> indices;

    /** Construct a pool with given number of elements and element array.
     */
    public Pool(int pp, Object[] pool) {
        this.pp = pp;
        this.pool = pool;
        this.indices = new HashMap<Object,Integer>(pool.length);
        for (int i = 1; i < pp; i++) {
            if (pool[i] != null) indices.put(pool[i], i);
        }
    }

    /** Construct an empty pool.
     */
    public Pool() {
        this(1, new Object[64]);
    }

    /** Return the number of entries in the constant pool.
     */
    public int numEntries() {
        return pp;
    }

    /** Remove everything from this pool.
     */
    public void reset() {
        pp = 1;
        indices.clear();
    }

    /** Double pool buffer in size.
     */
    private void doublePool() {
        Object[] newpool = new Object[pool.length * 2];
        System.arraycopy(pool, 0, newpool, 0, pool.length);
        pool = newpool;
    }

    /** Place an object in the pool, unless it is already there.
     *  If object is a symbol also enter its owner unless the owner is a
     *  package.  Return the object's index in the pool.
     */
    public int put(Object value) {
        // Backported by Ceylon from JDK8
        value = makePoolValue(value);
//      assert !(value instanceof Type.TypeVar);
        Integer index = indices.get(value);
        if (index == null) {
//          System.err.println("put " + value + " " + value.getClass());//DEBUG
            index = pp;
            indices.put(value, index);
            if (pp == pool.length) doublePool();
            pool[pp++] = value;
            if (value instanceof Long || value instanceof Double) {
                if (pp == pool.length) doublePool();
                pool[pp++] = null;
            }
        }
        return index.intValue();
    }

    // Backported by Ceylon from JDK8
    Object makePoolValue(Object o) {
        if (o instanceof DynamicMethodSymbol) {
            return new DynamicMethod((DynamicMethodSymbol)o);
        } else if (o instanceof MethodSymbol) {
            return new Method((MethodSymbol)o);
        } else if (o instanceof VarSymbol) {
            return new Variable((VarSymbol)o);
        } else {
            return o;
        }
    }

    /** Return the given object's index in the pool,
     *  or -1 if object is not in there.
     */
    public int get(Object o) {
        Integer n = indices.get(o);
        return n == null ? -1 : n.intValue();
    }

    static class Method extends DelegatedSymbol {
        MethodSymbol m;
        Method(MethodSymbol m) {
            super(m);
            this.m = m;
        }
        public boolean equals(Object other) {
            if (!(other instanceof Method)) return false;
            MethodSymbol o = ((Method)other).m;
            return
                o.name == m.name &&
                o.owner == m.owner &&
                o.type.equals(m.type);
        }
        public int hashCode() {
            return
                m.name.hashCode() * 33 +
                m.owner.hashCode() * 9 +
                m.type.hashCode();
        }
    }

    // Backported by Ceylon from JDK8
    static class DynamicMethod extends Method {
        public Object[] uniqueStaticArgs;

        DynamicMethod(DynamicMethodSymbol m) {
            super(m);
            uniqueStaticArgs = m.staticArgs;
        }

        @Override
        public boolean equals(Object any) {
            if (!super.equals(any)) return false;
            if (!(any instanceof DynamicMethod)) return false;
            DynamicMethodSymbol dm1 = (DynamicMethodSymbol)other;
            DynamicMethodSymbol dm2 = (DynamicMethodSymbol)((DynamicMethod)any).other;
            return dm1.bsm == dm2.bsm &&
                        dm1.bsmKind == dm2.bsmKind &&
                        Arrays.equals(uniqueStaticArgs,
                            ((DynamicMethod)any).uniqueStaticArgs);
        }

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            DynamicMethodSymbol dm = (DynamicMethodSymbol)other;
            hash += dm.bsmKind * 7 +
                    dm.bsm.hashCode() * 11;
            for (int i = 0; i < dm.staticArgs.length; i++) {
                hash += (uniqueStaticArgs[i].hashCode() * 23);
            }
            return hash;
        }
    }

    static class Variable extends DelegatedSymbol {
        VarSymbol v;
        Variable(VarSymbol v) {
            super(v);
            this.v = v;
        }
        public boolean equals(Object other) {
            if (!(other instanceof Variable)) return false;
            VarSymbol o = ((Variable)other).v;
            return
                o.name == v.name &&
                o.owner == v.owner &&
                o.type.equals(v.type);
        }
        public int hashCode() {
            return
                v.name.hashCode() * 33 +
                v.owner.hashCode() * 9 +
                v.type.hashCode();
        }
    }

    // Backported by Ceylon from JDK8
    public static class MethodHandle {

        /** Reference kind - see ClassFile */
        int refKind;

        /** Reference symbol */
        Symbol refSym;

        Type uniqueType;

        public MethodHandle(int refKind, Symbol refSym) {
            this.refKind = refKind;
            this.refSym = refSym;
            this.uniqueType = this.refSym.type;
            checkConsistent();
        }
        public boolean equals(Object other) {
            if (!(other instanceof MethodHandle)) return false;
            MethodHandle mr = (MethodHandle) other;
            if (mr.refKind != refKind)  return false;
            Symbol o = mr.refSym;
            return
                o.name == refSym.name &&
                o.owner == refSym.owner &&
                ((MethodHandle)other).uniqueType.equals(uniqueType);
        }
        public int hashCode() {
            return
                refKind * 65 +
                refSym.name.hashCode() * 33 +
                refSym.owner.hashCode() * 9 +
                uniqueType.hashCode();
        }

        /**
         * Check consistency of reference kind and symbol (see JVMS 4.4.8)
         */
        @SuppressWarnings("fallthrough")
        private void checkConsistent() {
            boolean staticOk = false;
            int expectedKind = -1;
            Filter<Name> nameFilter = nonInitFilter;
            boolean interfaceOwner = false;
            switch (refKind) {
                case ClassFile.REF_getStatic:
                case ClassFile.REF_putStatic:
                    staticOk = true;
                case ClassFile.REF_getField:
                case ClassFile.REF_putField:
                    expectedKind = Kinds.VAR;
                    break;
                case ClassFile.REF_newInvokeSpecial:
                    nameFilter = initFilter;
                    expectedKind = Kinds.MTH;
                    break;
                case ClassFile.REF_invokeInterface:
                    interfaceOwner = true;
                    expectedKind = Kinds.MTH;
                    break;
                case ClassFile.REF_invokeStatic:
                    interfaceOwner = true;
                    staticOk = true;
                case ClassFile.REF_invokeVirtual:
                case ClassFile.REF_invokeSpecial:
                    expectedKind = Kinds.MTH;
                    break;
            }
            Assert.check(!refSym.isStatic() || staticOk);
            Assert.check(refSym.kind == expectedKind);
            Assert.check(nameFilter.accepts(refSym.name));
            Assert.check(!refSym.owner.isInterface() || interfaceOwner);
        }
        //where
                Filter<Name> nonInitFilter = new Filter<Name>() {
                    public boolean accepts(Name n) {
                        return n != n.table.names.init && n != n.table.names.clinit;
                    }
                };

                Filter<Name> initFilter = new Filter<Name>() {
                    public boolean accepts(Name n) {
                        return n == n.table.names.init;
                    }
                };
    }
}
