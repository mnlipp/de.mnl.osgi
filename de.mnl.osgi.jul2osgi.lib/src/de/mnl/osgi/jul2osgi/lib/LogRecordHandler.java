/*
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

package de.mnl.osgi.jul2osgi.lib;

import java.util.logging.LogRecord;

/**
 * Called by {@link LogManager} in order to send the {@link LogRecord}
 * to the OSGi log service.
 */
public interface LogRecordHandler {

	/**
	 * Asks the handler to process the record. If the record has successfully
	 * been processed, returns {@code true}. If the record cannot be 
	 * processed, returns {@code false}. In the latter case, the record
	 * should be kept and processing should be attempted again when a
	 * new handler has been set. 
	 * 
	 * @param record the log record
	 * @return the result
	 */
	public boolean process(LogRecord record);

}
