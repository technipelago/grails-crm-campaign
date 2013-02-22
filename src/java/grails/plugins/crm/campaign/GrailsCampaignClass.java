/*
 * Copyright (c) 2012 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.crm.campaign;

import groovy.lang.Closure;
import org.codehaus.groovy.grails.commons.InjectableGrailsClass;

/**
 *
 * @author Goran Ehrsson
 */
public interface GrailsCampaignClass extends InjectableGrailsClass {

    public static final String TYPE = "Campaign";
    public static final String CONFIGURE = "configure";
    public static final String PROCESS = "process";

    void configure(Object campaign, Closure dsl);
    void process(Object data);
}
