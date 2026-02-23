/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.text;

import java.text.Format;
import java.text.MessageFormat;
import java.text.ParsePosition;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.matcher.StringMatcherFactory;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;

/**
 * Extends {@link java.text.MessageFormat} to allow pluggable/additional formatting
 * options for embedded format elements.
 * <p>
 * Client code should specify a registry
 * of {@code FormatFactory} instances associated with {@code String}
 * format names.  This registry will be consulted when the format elements are
 * parsed from the message pattern.  In this way custom patterns can be specified,
 * and the formats supported by {@link java.text.MessageFormat} can be overridden
 * at the format and/or format style level (see MessageFormat).  A "format element"
 * embedded in the message pattern is specified (<strong>()?</strong> signifies optionality):
 * </p>
 * <p>
 * {@code {}<em>argument-number</em><strong>(</strong>{@code ,}<em>format-name</em><b>
 * (</b>{@code ,}<em>format-style</em><strong>)?)?</strong>{@code }}
 * </p>
 *
 * <p>
 * <em>format-name</em> and <em>format-style</em> values are trimmed of surrounding whitespace
 * in the manner of {@link java.text.MessageFormat}.  If <em>format-name</em> denotes
 * {@code FormatFactory formatFactoryInstance} in {@code registry}, a {@code Format}
 * matching <em>format-name</em> and <em>format-style</em> is requested from
 * {@code formatFactoryInstance}.  If this is successful, the {@code Format}
 * found is used for this format element.
 * </p>
 *
 * <p><strong>NOTICE:</strong> The various subformat mutator methods are considered unnecessary; they exist on the parent
 * class to allow the type of customization which it is the job of this class to provide in
 * a configurable fashion.  These methods have thus been disabled and will throw
 * {@code UnsupportedOperationException} if called.
 * </p>
 *
 * <p>Limitations inherited from {@link java.text.MessageFormat}:</p>
 * <ul>
 * <li>When using "choice" subformats, support for nested formatting instructions is limited
 *     to that provided by the base class.</li>
 * <li>Thread-safety of {@code Format}s, including {@code MessageFormat} and thus
 *     {@code ExtendedMessageFormat}, is not guaranteed.</li>
 * </ul>
 *
 * @since 1.0
 */
public class ExtendedMessageFormat extends MessageFormat {

    /**
     * Serializable Object.
     */
    private static final long serialVersionUID = -2362048321261811743L;

    /**
     * The empty string.
     */
    private static final String EMPTY_PATTERN = StringUtils.EMPTY;

    /**
     * A comma.
     */
    private static final char START_FMT = ',';

    /**
     * A right curly bracket.
     */
    private static final char END_FE = '}';

    /**
     * A left curly bracket.
     */
    private static final char START_FE = '{';

    /**
     * A properly escaped character representing a single quote.
     */
    private static final char QUOTE = '\'';

    /**
     * To pattern string.
     */
    private String toPattern;

    /**
     * Our registry of FormatFactory.
     */
    private final Map<String, ? extends FormatFactory> registry;

    /**
     * Constructs a new ExtendedMessageFormat for the default locale.
     *
     * @param pattern  the pattern to use, not null.
     * @throws IllegalArgumentException in case of a bad pattern.
     */
    public ExtendedMessageFormat(final String pattern) {
        this(pattern, Locale.getDefault(Category.FORMAT));
    }

    /**
     * Constructs a new ExtendedMessageFormat.
     *
     * @param pattern  the pattern to use, not null.
     * @param locale  the locale to use, not null.
     * @throws IllegalArgumentException in case of a bad pattern.
     */
    public ExtendedMessageFormat(final String pattern, final Locale locale) {
        this(pattern, locale, null);
    }

    /**
     * Constructs a new ExtendedMessageFormat.
     *
     * @param pattern  the pattern to use, not null.
     * @param locale   the locale to use, not null.
     * @param registry the registry of format factories, may be null.
     * @throws IllegalArgumentException in case of a bad pattern.
     */
    public ExtendedMessageFormat(final String pattern, final Locale locale, final Map<String, ? extends FormatFactory> registry) {
        super(EMPTY_PATTERN);
        setLocale(locale);
        this.registry = registry != null ? Collections.unmodifiableMap(new UnifiedMap<>(registry)) : null;
        applyPattern(pattern);
    }

    /**
     * Constructs a new ExtendedMessageFormat for the default locale.
     *
     * @param pattern  the pattern to use, not null.
     * @param registry the registry of format factories, may be null.
     * @throws IllegalArgumentException in case of a bad pattern.
     */
    public ExtendedMessageFormat(final String pattern, final Map<String, ? extends FormatFactory> registry) {
        this(pattern, Locale.getDefault(Category.FORMAT), registry);
    }

    /**
     * Consumes a quoted string, adding it to {@code appendTo} if specified.
     *
     * @param pattern  pattern to parse.
     * @param pos      current parse position.
     * @param appendTo optional StringBuilder to append.
     */
    private void appendQuotedString(final String pattern, final ParsePosition pos, final StringBuilder appendTo) {
        assert pattern.toCharArray()[pos.getIndex()] == QUOTE : "Quoted string must start with quote character";
        // handle quote character at the beginning of the string
        if (appendTo != null) {
            appendTo.append(QUOTE);
        }
        next(pos);
        final int start = pos.getIndex();
        final char[] c = pattern.toCharArray();
        final int patternLength = pattern.length();
        for (int i = pos.getIndex(); i < patternLength; ++i) {
            switch (c[pos.getIndex()]) {
            case QUOTE:
                next(pos);
                if (appendTo != null) {
                    appendTo.append(c, start, pos.getIndex() - start);
                }
                return;
            default:
                next(pos);
            }
        }
        throw new IllegalArgumentException("Unterminated quoted string at position " + start);
    }

    /**
     * Applies the specified pattern.
     *
     * @param pattern String.
     */
    @Override
    public final void applyPattern(final String pattern) {
        if (registry == null) {
            super.applyPattern(pattern);
            toPattern = super.toPattern();
            return;
        }
        final FastList<Format> foundFormats = new FastList<>();
        final FastList<String> foundDescriptions = new FastList<>();
        final StringBuilder stripCustom = new StringBuilder(pattern.length());
        final ParsePosition pos = new ParsePosition(0);
        final char[] c = pattern.toCharArray();
        final int patternLength = pattern.length();
        int fmtCount = 0;
        while (pos.getIndex() < patternLength) {
            switch (c[pos.getIndex()]) {
            case QUOTE:
                appendQuotedString(pattern, pos, stripCustom);
                break;
            case START_FE:
                ++fmtCount;
                seekNonWs(pattern, pos);
                final int start = pos.getIndex();
                final int index = readArgumentIndex(pattern, next(pos));
                stripCustom.append(START_FE).append(index);
                seekNonWs(pattern, pos);
                Format format = null;
                String formatDescription = null;
                if (c[pos.getIndex()] == START_FMT) {
                    formatDescription = parseFormatDescription(pattern, next(pos));
                    format = getFormat(formatDescription);
                    if (format == null) {
                        stripCustom.append(START_FMT).append(formatDescription);
                    }
                }
                foundFormats.add(format);
                foundDescriptions.add(format == null ? null : formatDescription);
                if (foundFormats.size() != fmtCount) {
                    throw new IllegalArgumentException("The validated expression is false");
                }
                if (foundDescriptions.size() != fmtCount) {
                    throw new IllegalArgumentException("The validated expression is false");
                }
                if (c[pos.getIndex()] != END_FE) {
                    throw new IllegalArgumentException("Unreadable format element at position " + start);
                }
                //$FALL-THROUGH$
            default:
                stripCustom.append(c[pos.getIndex()]);
                next(pos);
            }
        }
        super.applyPattern(stripCustom.toString());
        toPattern = insertFormats(super.toPattern(), foundDescriptions);
        if (containsElements(foundFormats)) {
            final Format[] origFormats = getFormats();
            // only loop over what we know we have, as MessageFormat on Java 1.3
            // seems to provide an extra format element:
            final int foundFormatsSize = foundFormats.size();
            for (int i = 0; i < foundFormatsSize; ++i) {
                final Format f = foundFormats.get(i);
                if (f != null) {
                    origFormats[i] = f;
                }
            }
            super.setFormats(origFormats);
        }
    }

    /**
     * Tests whether the specified Collection contains non-null elements.
     *
     * @param coll to check.
     * @return {@code true} if some Object was found, {@code false} otherwise.
     */
    private boolean containsElements(final Collection<?> coll) {
        if (coll == null || coll.isEmpty()) {
            return false;
        }
        return coll.stream().anyMatch(Objects::nonNull);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof ExtendedMessageFormat)) {
            return false;
        }
        final ExtendedMessageFormat other = (ExtendedMessageFormat) obj;
        return Objects.equals(registry, other.registry) && Objects.equals(toPattern, other.toPattern);
    }

    /**
     * Gets a custom format from a format description.
     *
     * @param desc String.
     * @return Format.
     */
    private Format getFormat(final String desc) {
        if (registry != null) {
            String name = desc;
            String args = null;
            final int i = desc.indexOf(START_FMT);
            if (i > 0) {
                name = desc.substring(0, i).trim();
                args = desc.substring(i + 1).trim();
            }
            final FormatFactory factory = registry.get(name);
            if (factory != null) {
                return factory.getFormat(name, args, getLocale());
            }
        }
        return null;
    }

    /**
     * Consumes quoted string only.
     *
     * @param pattern pattern to parse.
     * @param pos current parse position.
     */
    private void getQuotedString(final String pattern, final ParsePosition pos) {
        appendQuotedString(pattern, pos, null);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        final int result = super.hashCode();
        return prime * result + Objects.hash(registry, toPattern);
    }

    /**
     * Inserts formats back into the pattern for toPattern() support.
     *
     * @param pattern source.
     * @param customPatterns The custom patterns to re-insert, if any.
     * @return full pattern.
     */
    private String insertFormats(final String pattern, final FastList<String> customPatterns) {
        if (!containsElements(customPatterns)) {
            return pattern;
        }
        final StringBuilder sb = new StringBuilder(pattern.length() * 2);
        final ParsePosition pos = new ParsePosition(0);
        int fe = -1;
        int depth = 0;
        final int patternLength = pattern.length();
        while (pos.getIndex() < patternLength) {
            final char c = pattern.charAt(pos.getIndex());
            switch (c) {
            case QUOTE:
                appendQuotedString(pattern, pos, sb);
                break;
            case START_FE:
                ++depth; // GCI67: Changed depth++ to ++depth
                sb.append(START_FE).append(readArgumentIndex(pattern, next(pos)));
                // do not look for custom patterns when they are embedded, e.g. in a choice
                if (depth == 1) {
                    ++fe; // GCI67: Changed fe++ to ++fe
                    final String customPattern = customPatterns.get(fe);
                    if (customPattern != null) {
                        sb.append(START_FMT).append(customPattern);
                    }
                }
                break;
            case END_FE:
                --depth; // GCI67: Changed depth-- to --depth (assuming similar intent for decrement)
                //$FALL-THROUGH$
            default:
                sb.append(c);
                next(pos);
            }
        }
        return sb.toString();
    }

    /**
     * Advances parse position by 1.
     *
     * @param pos ParsePosition.
     * @return {@code pos}.
     */
    private ParsePosition next(final ParsePosition pos) {
        pos.setIndex(pos.getIndex() + 1);
        return pos;
    }

    /**
     * Parses the format component of a format element.
     *
     * @param pattern string to parse.
     * @param pos current parse position.
     * @return Format description String.
     */
    private String parseFormatDescription(final String pattern, final ParsePosition pos) {
        final int start = pos.getIndex();
        seekNonWs(pattern, pos);
        final int text = pos.getIndex();
        int depth = 1;
        final int patternLength = pattern.length();
        while (pos.getIndex() < patternLength) {
            switch (pattern.charAt(pos.getIndex())) {
            case START_FE:
                ++depth;
                next(pos);
                break;
            case END_FE:
                --depth;
                if (depth == 0) {
                    return pattern.substring(text, pos.getIndex());
                }
                next(pos);
                break;
            case QUOTE:
                getQuotedString(pattern, pos);
                break;
            default:
                next(pos);
                break;
            }
        }
        throw new IllegalArgumentException(
                "Unterminated format element at position " + start);
    }

    /**
     * Reads the argument index from the current format element.
     *
     * @param pattern pattern to parse.
     * @param pos current parse position.
     * @return argument index.
     */
    private int readArgumentIndex(final String pattern, final ParsePosition pos) {
        final int start = pos.getIndex();
        seekNonWs(pattern, pos);
        // After seekNonWs, pos.getIndex() might have changed, so initialize currentIndex here.
        int currentIndex = pos.getIndex();

        final StringBuilder result = new StringBuilder(10);
        boolean error = false;
        final int patternLength = pattern.length();

        // GCI69: Refactored from for-loop to while-loop to avoid function call in loop declaration.
        while (!error && currentIndex < patternLength) {
            char c = pattern.charAt(currentIndex);
            if (Character.isWhitespace(c)) {
                seekNonWs(pattern, pos);
                currentIndex = pos.getIndex(); // Synchronize currentIndex after seekNonWs
                // After seeking non-whitespace, check if we've reached the end of the pattern
                if (currentIndex >= patternLength) {
                    error = true;
                    next(pos); // Advance pos one last time to match original behavior for error reporting
                    currentIndex = pos.getIndex(); // Synchronize currentIndex after next(pos)
                    break;
                }
                c = pattern.charAt(currentIndex);
                if (c != START_FMT && c != END_FE) {
                    error = true;
                    next(pos); // Advance pos one last time to match original behavior for error reporting
                    currentIndex = pos.getIndex(); // Synchronize currentIndex after next(pos)
                    break;
                }
            }
            if ((c == START_FMT || c == END_FE) && result.length() > 0) {
                // GCI28: Added explicit numeric validation before parsing
                final String argIndexStr = result.toString();
                if (!StringUtils.isNumeric(argIndexStr)) {
                    throw new IllegalArgumentException(
                            "Invalid format argument index (non-numeric) at position " + start + ": "
                                    + pattern.substring(start, currentIndex));
                }
                return Integer.parseInt(argIndexStr);
            }
            error = !Character.isDigit(c);
            if (error) { // If it's not a digit, and not START_FMT/END_FE, it's an error
                next(pos); // Advance pos one last time to match original behavior for error reporting
                currentIndex = pos.getIndex(); // Synchronize currentIndex after next(pos)
                break;
            }
            result.append(c);
            next(pos); // GCI69: next(pos) moved here from the for-loop update clause
            currentIndex = pos.getIndex(); // Synchronize currentIndex after next(pos)
        }
        if (error) {
            throw new IllegalArgumentException(
                    "Invalid format argument index at position " + start + ": "
                            + pattern.substring(start, currentIndex));
        }
        throw new IllegalArgumentException(
                "Unterminated format element at position " + start);
    }

    /**
     * Consumes whitespace from the current parse position.
     *
     * @param pattern String to read.
     * @param pos current position.
     */
    private void seekNonWs(final String pattern, final ParsePosition pos) {
        int len = 0;
        final char[] buffer = pattern.toCharArray();
        final int bufferLength = buffer.length;
        do {
            len = StringMatcherFactory.INSTANCE.splitMatcher().isMatch(buffer, pos.getIndex(), 0, bufferLength);
            pos.setIndex(pos.getIndex() + len);
        } while (len > 0 && pos.getIndex() < bufferLength);
    }

    /**
     * Throws UnsupportedOperationException, see class Javadoc for details.
     *
     * @param formatElementIndex format element index.
     * @param newFormat          the new format.
     * @throws UnsupportedOperationException always thrown since this isn't supported by {@link ExtendedMessageFormat}.
     */
    @Override
    public void setFormat(final int formatElementIndex, final Format newFormat) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws UnsupportedOperationException, see class Javadoc for details.
     *
     * @param argumentIndex argument index.
     * @param newFormat     the new format.
     * @throws UnsupportedOperationException always thrown since this isn't supported by {@link ExtendedMessageFormat}.
     */
    @Override
    public void setFormatByArgumentIndex(final int argumentIndex,
                                         final Format newFormat) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws UnsupportedOperationException - see class Javadoc for details.
     *
     * @param newFormats new formats.
     * @throws UnsupportedOperationException always thrown since this isn't supported by {@link ExtendedMessageFormat}.
     */
    @Override
    public void setFormats(final Format[] newFormats) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws UnsupportedOperationException - see class Javadoc for details.
     *
     * @param newFormats new formats
     * @throws UnsupportedOperationException always thrown since this isn't supported by {@link ExtendedMessageFormat}
     */
    @Override
    public void setFormatsByArgumentIndex(final Format[] newFormats) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toPattern() {
        return toPattern;
    }
}