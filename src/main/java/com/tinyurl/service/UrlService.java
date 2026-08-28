package com.tinyurl.service;

import com.tinyurl.dto.CreateUrlResponse;
import com.tinyurl.dto.UrlAnalyticsResponse;


public interface UrlService {

    CreateUrlResponse createShortUrl(String originalUrl) ;
    String resolveAndRecordRedirect(String shortCode);
    UrlAnalyticsResponse getAnalytics(String shortCode) ;

}
