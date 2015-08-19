// Copyright (c) 2015 Uber
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.uber.jenkins.phabricator.provider;

import com.uber.jenkins.phabricator.utils.Logger;
import jenkins.model.Jenkins;

public class BaseProvider<T> extends Provider<T> {
    public BaseProvider(Jenkins jenkins, String pluginName, Logger logger) {
        super(jenkins, pluginName, logger);
    }

    /**
     * Instantiate a new instance of the class given the implementation class name
     * @param implementationName The fully-qualified name of the class
     * @return An instance of the class
     */
    @Override
    public T getInstance(final String implementationName) {
        try {
            return (T) getClass().getClassLoader().loadClass(implementationName).newInstance();
        } catch (ClassNotFoundException e) {
            e.printStackTrace(logger.getStream());
            return null;
        } catch (InstantiationException e) {
            e.printStackTrace(logger.getStream());
        } catch (IllegalAccessException e) {
            e.printStackTrace(logger.getStream());
        }
        return null;
    }
}
