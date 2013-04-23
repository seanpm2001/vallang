/*******************************************************************************
 * Copyright (c) 2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *
 * Based on code by:
 *
 *   * Robert Fuhrer (rfuhrer@watson.ibm.com) - initial API and implementation
 *******************************************************************************/
package org.eclipse.imp.pdb.facts.impl.reference;

import org.eclipse.imp.pdb.facts.IListRelation;
import org.eclipse.imp.pdb.facts.IListRelationWriter;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.type.Type;
import org.eclipse.imp.pdb.facts.type.TypeFactory;

/*package*/ class ListRelationWriter extends ListWriter implements IListRelationWriter {

    /*package*/ ListRelationWriter(Type eltType) {
        super(eltType);
    }

    /*package*/ ListRelationWriter() {
        super();
    }

    public IListRelation done() {
    	// Temporary fix of the static vs dynamic type issue
    	eltType = TypeFactory.getInstance().voidType();
    	for(IValue el : listContent)
    		eltType = eltType.lub(el.getType());
    	// ---
        if (constructedList == null) {
            constructedList = ListOrRel.apply(eltType, listContent);
        }
        return (IListRelation) constructedList;
    }

}