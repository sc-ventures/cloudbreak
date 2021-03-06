package com.sequenceiq.cloudbreak.converter;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cloud.model.AmbariRepo;
import com.sequenceiq.cloudbreak.cloud.model.catalog.Image;

@Component
public class ImageToAmbariRepoConverter extends AbstractConversionServiceAwareConverter<Image, AmbariRepo> {

    @Override
    public AmbariRepo convert(Image image) {
        AmbariRepo ambariRepo = new AmbariRepo();
        ambariRepo.setPredefined(Boolean.TRUE);
        ambariRepo.setVersion(image.getVersion());
        ambariRepo.setBaseUrl(image.getRepo().get(image.getOsType()));
        return ambariRepo;
    }
}
