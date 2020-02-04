package com.google.cloud.healthcare.imaging.dicomadapter;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;

import java.util.Objects;

public class DestinationFilter {
    private static final String AET_TITLE = "AETTitle";

    private final String aetTitle;
    private final Attributes filterAttrs;

    public DestinationFilter(String filterString) {
        String aetTitle = null;
        this.filterAttrs = new Attributes();

        if(filterString != null && filterString.length() > 0) {
            String[] params = filterString.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue[0].equals(AET_TITLE)) {
                    aetTitle = keyValue[1];
                } else {
                    int tag = TagUtils.forName(keyValue[0]);
                    if (tag == -1) {
                        throw new IllegalArgumentException(
                            "Invalid tag in filter string: " + keyValue[0]);
                    }
                    this.filterAttrs.setString(tag, VR.LO,
                        keyValue[1]); // VR just needs to be any string type for match()
                }
            }
        }

        this.aetTitle = aetTitle;
    }

    public String getAetTitle() {
        return aetTitle;
    }

    public Attributes getFilterAttrs() {
        return filterAttrs;
    }

    public boolean matches(String incomingAet, Attributes incomingAttrs) {
        return (aetTitle == null || aetTitle.equals(incomingAet)) &&
            (this.filterAttrs.size() == 0
                || incomingAttrs.matches(this.filterAttrs, false, false));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DestinationFilter that = (DestinationFilter) o;
        return Objects.equals(aetTitle, that.aetTitle) &&
                filterAttrs.equals(that.filterAttrs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aetTitle, filterAttrs);
    }
}
