/**
 * Copyright (C) 2019 Michael N. Lipp (http://www.mnl.de)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.mnl.osgi.coreutils.test;

public class SampleService1 {

    public SampleService1() {
    }

    /**
     * Adds the parameter values.
     *
     * @param valueA the a
     * @param valueB the b
     * @return the int
     */
    public int add(int valueA, int valueB) {
        return valueA + valueB;
    }
}
