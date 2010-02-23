package org.mightyfrog.util.zip;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 *
 * @author Shigehiro Soejima
 */
class I18N  {
    //
    public static final String BASE = "i18n";

    //
    private static ResourceBundle _rb = null;

    /**
     *
     */
    private I18N() {
        //
    }

    /**
     *
     * @param locale
     */
    static void init() {
        _rb = ResourceBundle.getBundle(BASE, Locale.getDefault());
        //_rb = ResourceBundle.getBundle(BASE, Locale.ROOT);
    }

    /**
     *
     */
    static Locale getLocale() {
        return _rb.getLocale();
    }

    /**
     *
     * @param key
     */
    static String get(String key) {
        if (_rb == null) {
            init();
        }
        String value = null;
        try {
            value =_rb.getString(key);
        } catch (MissingResourceException e) {
        }

        return value;
    }

    /**
     *
     * @param key
     * @param inserts
     */
    static String get(String key, Object... inserts) {
        String s = get(key);
        if (s != null) {
            for (int i = 0; i < inserts.length; i++) {
                s = s.replace("{" + i + "}", String.valueOf((inserts[i])));
            }
        }

        return s;
    }
}
