/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.jersey.incubator.html.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import net.java.html.json.Context;
import net.java.html.json.Model;
import net.java.html.json.Models;
import net.java.html.json.Property;
import org.apidesign.html.json.spi.ContextBuilder;
import org.apidesign.html.json.spi.ContextProvider;
import org.apidesign.html.json.spi.JSONCall;
import org.apidesign.html.json.spi.Transfer;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/** Implementation of Jersey's message body reader and writer that
 * can handle reading and writing of JSON models generated by {@link Model}
 * annotation provided by <code>net.java.html.json</code> library. Include
 * this JAR in your project and you can then use your 
 * model classes as Jersey's entities.
 * <p>
 * <pre>
 * {@link Model @Model}(className="Query", properties={
 *   {@link Property @Property}(name="items", type=Item.<b>class</b>, array=true)
 * })
 * <b>class</b> QueryImpl {
 * 
 *   {@link Model @Model}(className="Item", properties={
 *     {@link Property @Property}(name="id", type=String.<b>class</b>),
 *     {@link Property @Property}(name="kind", type=Kind.<b>class</b>) 
 *   })
 *   <b>class</b> ItemImpl {
 *   }
 * 
 *   <b>enum</b> Kind {
 *     GOOD, BAD
 *   }
 * 
 *   <b>public static</b> List{@code <Item>} doQuery() {
 *     {@link WebTarget} target = ...;
 *     Query q = target.request(MediaType.APPLICATION_JSON).get().readEntity(Query.<b>class</b>);
 *     return q.getItems();
 *   }
 * }
 * </pre>
 *
 * @author Jaroslav Tulach <jtulach@netbeans.org>
 */
@ServiceProviders({
    @ServiceProvider(service = MessageBodyWriter.class),
    @ServiceProvider(service = MessageBodyReader.class),
    @ServiceProvider(service = ContextProvider.class),
})
public final class HtmlJsonMsgs 
implements MessageBodyWriter, MessageBodyReader<Object>, ContextProvider,
Transfer {
    private static final Logger LOG = Logger.getLogger(HtmlJsonMsgs.class.getName());
    private static final Context CONTEXT;
    static {
        HtmlJsonMsgs w = new HtmlJsonMsgs();
        CONTEXT = ContextBuilder.create().withTransfer(w).build();
    }

    public HtmlJsonMsgs() {
    }
    
    @Override
    public boolean isWriteable(Class type, Type type1, Annotation[] antns, MediaType mt) {
        if (!mt.equals(MediaType.APPLICATION_JSON_TYPE)) {
            return false;
        }
        return Models.isModel(type);
    }

    @Override
    public long getSize(Object t, Class type, Type type1, Annotation[] antns, MediaType mt) {
        return -1;
    }

    @Override
    public void writeTo(Object t, Class type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap mm, OutputStream out) throws IOException, WebApplicationException {
        out.write(t.toString().getBytes("UTF-8"));
    }

    @Override
    public boolean isReadable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
        return isWriteable(type, type1, antns, mt);
    }

    @Override
    public Object readFrom(Class<Object> type, 
        Type type1, Annotation[] antns, MediaType mt, 
        MultivaluedMap<String, String> mm, 
        InputStream in
    ) throws IOException, WebApplicationException {
        return Models.parse(CONTEXT, type, in);
    }

    @Override
    public Context findContext(Class<?> requestor) {
        return CONTEXT;
    }

    @Override
    public void extract(Object jsonObject, String[] props, Object[] values) {
        if (jsonObject instanceof JSONObject) {
            JSONObject obj = (JSONObject) jsonObject;
            for (int i = 0; i < props.length; i++) {
                try {
                    values[i] = obj.has(props[i]) ? obj.get(props[i]) : null;
                } catch (JSONException ex) {
                    LOG.log(Level.SEVERE, "Can't read " + props[i] + " from " + jsonObject, ex);
                }
            }
        }
    }

    @Override
    public Object toJSON(InputStream is) throws IOException {
        try {
            InputStreamReader r = new InputStreamReader(is, "UTF-8");
            JSONTokener t = new JSONTokener(r);
            return new JSONObject(t);
        } catch (JSONException ex) {
            throw new IOException(ex);
        }
        
    }

    @Override
    public void loadJSON(JSONCall call) {
        throw new UnsupportedOperationException();
    }
}
