/*
 * ManlyFastFerryPreambleRecord.java
 *
 * Copyright 2015 Michael Farrell <micolous+git@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package au.id.micolous.metrodroid.transit.manly_fast_ferry.record;

import au.id.micolous.metrodroid.transit.manly_fast_ferry.ManlyFastFerryTransitData;
import au.id.micolous.metrodroid.util.Utils;

import java.util.Arrays;

/**
 * Represents a "preamble" type record.
 */
public class ManlyFastFerryPreambleRecord extends ManlyFastFerryRecord {
    static final byte[] OLD_CARD_ID = {0x00, 0x00, 0x00};
    private String mCardSerial;

    protected ManlyFastFerryPreambleRecord() {
    }

    public static ManlyFastFerryPreambleRecord recordFromBytes(byte[] input) {
        ManlyFastFerryPreambleRecord record = new ManlyFastFerryPreambleRecord();

        // Check that the record is valid for a preamble
        if (!Arrays.equals(Arrays.copyOfRange(input, 0, ManlyFastFerryTransitData.SIGNATURE.length), ManlyFastFerryTransitData.SIGNATURE)) {
            throw new IllegalArgumentException("Preamble signature does not match");
        }

        // This is not set on 2012-era cards
        if (Arrays.equals(Arrays.copyOfRange(input, 10, 13), OLD_CARD_ID)) {
            record.mCardSerial = null;
        } else {
            record.mCardSerial = Utils.getHexString(Arrays.copyOfRange(input, 10, 14));
        }
        return record;
    }

    /**
     * Returns the card serial number. Returns null on old cards.
     */
    public String getCardSerial() {
        return mCardSerial;
    }

}