/*
 * ClassicCard.java
 *
 * Copyright (C) 2012 Eric Butler
 *
 * Authors:
 * Wilbert Duijvenvoorde <w.a.n.duijvenvoorde@gmail.com>
 * Eric Butler <eric@codebutler.com>
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

package au.id.micolous.farebot.card.classic;

import android.nfc.Tag;
import android.nfc.tech.MifareClassic;

import au.id.micolous.farebot.card.Card;
import au.id.micolous.farebot.card.CardHasManufacturingInfo;
import au.id.micolous.farebot.card.CardRawDataFragmentClass;
import au.id.micolous.farebot.card.CardType;
import au.id.micolous.farebot.fragment.ClassicCardRawDataFragment;
import au.id.micolous.farebot.key.CardKeys;
import au.id.micolous.farebot.key.ClassicCardKeys;
import au.id.micolous.farebot.key.ClassicSectorKey;
import au.id.micolous.farebot.transit.TransitData;
import au.id.micolous.farebot.transit.TransitIdentity;
import au.id.micolous.farebot.transit.bilhete_unico.BilheteUnicoSPTransitData;
import au.id.micolous.farebot.transit.ovc.OVChipTransitData;
import au.id.micolous.farebot.transit.unknown.UnauthorizedClassicTransitData;
import au.id.micolous.farebot.util.Utils;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Root(name="card")
@CardRawDataFragmentClass(ClassicCardRawDataFragment.class)
@CardHasManufacturingInfo(false)
public class ClassicCard extends Card {
    public static final byte[] PREAMBLE_KEY = { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };

    @ElementList(name="sectors") private List<ClassicSector> mSectors;

    private ClassicCard() { /* For XML Serializer */ }

    protected ClassicCard(byte[] tagId, Date scannedAt, ClassicSector[] sectors) {
        super(CardType.MifareClassic, tagId, scannedAt);
        mSectors = Utils.arrayAsList(sectors);
    }

    public static ClassicCard dumpTag(byte[] tagId, Tag tag) throws Exception {
        MifareClassic tech = null;

        try {
            tech = MifareClassic.get(tag);
            tech.connect();

            ClassicCardKeys keys = (ClassicCardKeys) CardKeys.forTagId(tagId);

            List<ClassicSector> sectors = new ArrayList<>();

            for (int sectorIndex = 0; sectorIndex < tech.getSectorCount(); sectorIndex++) {
                try {
                    boolean authSuccess = false;

                    ClassicSectorKey sectorKey;
                    if (keys != null && (sectorKey = keys.keyForSector(sectorIndex)) != null) {
                        if (sectorKey.getType().equals(ClassicSectorKey.TYPE_KEYA)) {
                            authSuccess = tech.authenticateSectorWithKeyA(sectorIndex, sectorKey.getKey());
                        } else {
                            authSuccess = tech.authenticateSectorWithKeyB(sectorIndex, sectorKey.getKey());
                        }
                    }

                    if (!authSuccess && sectorIndex == 0) {
                        authSuccess = tech.authenticateSectorWithKeyA(sectorIndex, PREAMBLE_KEY);
                    }

                    if (!authSuccess) {
                        authSuccess = tech.authenticateSectorWithKeyA(sectorIndex, MifareClassic.KEY_DEFAULT);
                    }

                    if (authSuccess) {
                        List<ClassicBlock> blocks = new ArrayList<>();
                        // FIXME: First read trailer block to get type of other blocks.
                        int firstBlockIndex = tech.sectorToBlock(sectorIndex);
                        for (int blockIndex = 0; blockIndex < tech.getBlockCountInSector(sectorIndex); blockIndex++) {
                            byte[] data = tech.readBlock(firstBlockIndex + blockIndex);
                            String type = ClassicBlock.TYPE_DATA; // FIXME
                            blocks.add(ClassicBlock.create(type, blockIndex, data));
                        }
                        sectors.add(new ClassicSector(sectorIndex, blocks.toArray(new ClassicBlock[blocks.size()])));
                    } else {
                        sectors.add(new UnauthorizedClassicSector(sectorIndex));
                    }
                } catch (IOException ex) {
                    sectors.add(new InvalidClassicSector(sectorIndex, Utils.getErrorMessage(ex)));
                }
            }

            return new ClassicCard(tagId, new Date(), sectors.toArray(new ClassicSector[sectors.size()]));

        } finally {
            if (tech != null && tech.isConnected()) {
                tech.close();
            }
        }
    }

    @Override public TransitIdentity parseTransitIdentity() {
        // All .check() methods should work without a key, and throw an UnauthorizedException
        // Otherwise UnauthorizedClassicTransitData will not trigger
        if (OVChipTransitData.check(this)) {
            return OVChipTransitData.parseTransitIdentity(this);
        } else if (BilheteUnicoSPTransitData.check(this)) {
            return BilheteUnicoSPTransitData.parseTransitIdentity(this);
        } else if (UnauthorizedClassicTransitData.check(this)) {
            // This check must be LAST.
            //
            // This is to throw up a warning whenever there is a card with all locked sectors
            return UnauthorizedClassicTransitData.parseTransitIdentity(this);
        }

        // This point is where the card has some open sectors, but cannot be identified.
        return null;
    }

    @Override public TransitData parseTransitData() {
        if (OVChipTransitData.check(this)) {
            return new OVChipTransitData(this);
        } else if (BilheteUnicoSPTransitData.check(this)) {
            return new BilheteUnicoSPTransitData(this);
        } else if (UnauthorizedClassicTransitData.check(this)) {
            return new UnauthorizedClassicTransitData();
        }
        return null;
    }

    public List<ClassicSector> getSectors() {
        return mSectors;
    }

    public ClassicSector getSector(int index) {
        return mSectors.get(index);
    }
}