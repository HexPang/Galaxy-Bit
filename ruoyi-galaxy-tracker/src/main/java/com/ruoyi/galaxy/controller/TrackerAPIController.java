package com.ruoyi.galaxy.controller;

import com.dampcake.bencode.Bencode;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.OperatorType;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.galaxy.domain.*;
import com.ruoyi.galaxy.service.IGlxPeerService;
import com.ruoyi.galaxy.service.IGlxPointsRecordService;
import com.ruoyi.galaxy.service.IGlxTorrentAttachmentService;
import com.ruoyi.galaxy.service.IGlxTorrentPurchaseService;
import com.ruoyi.galaxy.service.impl.GlxTorrentServiceImpl;
import com.ruoyi.galaxy.util.BitConvert;
import com.ruoyi.galaxy.util.ConfigUtil;
import com.ruoyi.galaxy.util.PointsUtil;
import com.ruoyi.galaxy.vo.AnnounceVO;
import com.ruoyi.system.service.ISysUserService;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriUtils;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/announce")
public class TrackerAPIController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(TrackerAPIController.class);


    @Autowired
    protected HttpServletRequest request;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private GlxTorrentServiceImpl torrentService;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private IGlxPeerService peerService;

    @Autowired
    private IGlxTorrentPurchaseService torrentPurchaseService;

    @Autowired
    private IGlxPointsRecordService pointsRecordService;

    @Autowired
    private IGlxTorrentAttachmentService attachmentService;

    @Autowired
    private PointsUtil pointsUtil;

    public void banded(String ip, Integer time) {
        String key = "ban_ip_" + ip;
        redisCache.setCacheObject(key, true, time, TimeUnit.SECONDS);
    }

    public boolean checkBanded (AnnounceVO announceVO) {
        String key = "ban_ip_" + IpUtils.getIpAddr(request);
        return redisCache.getCacheObject(key) != null;
    }

    private String getIp() {
        String[] ips = IpUtils.getIpAddr(request).split(",");
        return ips[0].trim();
    }
    /**
     * ????????????
     * @param announceVO
     * @return
     */
    public boolean checkCheating (AnnounceVO announceVO, SysUser user) {
        if (announceVO.getDownloaded() < 0 || announceVO.getUploaded() < 0) {
            log.error("[CHEAT] ????????????: " + announceVO.getToken() + " ????????????: " + announceVO.getPeer_id() + " ??????????????????????????????: " + announceVO.getDownloaded() + ":" + announceVO.getUploaded());
            banded(getIp(), 1800);
            return true;
        }
        List<GlxPeer> peers = peerService.selectGlxPeerByUserIdGroupByIP(user.getUserId());
        if (peers.size() > 2) {
            // ??????????????????IP!?????????
            log.error("[CHEAT] ????????????: " + announceVO.getToken() + " ????????????: " + announceVO.getPeer_id() + " IP????????????: " + peers.size());
            banded(getIp(), 1800);
            return true;
        }
        // TODO: 2021/3/19  ??????????????????????????????
        return false;
    }

    private void lockPeer(AnnounceVO announceVO) {
        String peerLocker = "peer_locker_" + announceVO.getPeer_id();
        redisCache.setCacheObject(peerLocker, true, 3, TimeUnit.SECONDS);
    }

    private void unlockPeer(AnnounceVO announceVO) {
        String peerLocker = "peer_locker_" + announceVO.getPeer_id();
        redisCache.deleteObject(peerLocker);
    }

    private boolean checkPeerLocker(AnnounceVO announceVO) {
        String peerLocker = "peer_locker_" + announceVO.getPeer_id();
        return redisCache.getCacheObject(peerLocker) != null;
    }

    @GetMapping
    public String announce(AnnounceVO announceVO) throws UnsupportedEncodingException {
//        if (checkPeerLocker(announceVO)) {
//            return "";
//        }
//        lockPeer(announceVO);
        if (StringUtils.isEmpty(announceVO.getPeer_id())) {
            return error("ERROR_PEER_ID", announceVO);
        }
        if (!StringUtils.isNotEmpty(announceVO.getToken())) {
            return error("ERROR_ACCESS_TOKEN", announceVO);
        }
        if (checkBanded(announceVO)) {
            return error("ERROR_BANDED", announceVO);
        }
        SysUser user = userService.selectUserByToken(announceVO.getToken());
        if (user == null) {
            return error("ERROR_ACCESS_TOKEN_NOT_EXISTS", announceVO);
        }
        String queryString = request.getQueryString();
        System.out.println(queryString);
        String infoHash = queryString.substring(queryString.indexOf("info_hash=") + 10);
        infoHash = infoHash.substring(0, infoHash.indexOf("&"));
        infoHash = BitConvert.byteArrayToHexString(UriUtils.decode(infoHash, "ISO-8859-1").getBytes("ISO-8859-1")).toLowerCase();
        String torrentInfoHash = null;
        String attachmentInfoHash = null;
        if (announceVO.getType() == null) {
            announceVO.setType("torrent");
        }
        if (announceVO.getType().equals("attachment")) {
            attachmentInfoHash = infoHash;
        } else {
            torrentInfoHash = infoHash;
        }

        if (StringUtils.isEmpty(torrentInfoHash) && StringUtils.isEmpty(attachmentInfoHash)) {
            return error("ERROR_TORRENT", announceVO);
        }
        GlxTorrent torrent = null;
        GlxTorrentAttachment attachment = null;
        if (StringUtils.isNotEmpty(attachmentInfoHash)) {
            attachment = attachmentService.selectGlxTorrentAttachmentByInfoHash(attachmentInfoHash);
            if (attachment == null) {
                return error("ERROR_ATTACHMENT_NOT_FOUND", announceVO);
            } else {
                torrent = torrentService.selectGlxTorrentById(attachment.getTorrentId());
                torrentInfoHash = torrent.getInfoHash();
            }
        } else {
            torrent = torrentService.selectGlxTorrentByInfoHash(torrentInfoHash);
        }
        if (torrent == null) {
            return error("ERROR_TORRENT_NOT_EXISTS", announceVO);
        }
        GlxTorrentPurchase glxTorrentPurchase = null;
        if (!torrent.getUserId().equals(user.getUserId())) {
            GlxTorrentPurchase filter = new GlxTorrentPurchase();
            filter.setUserId(user.getUserId());
            filter.setTorrentId(torrent.getId());
            List<GlxTorrentPurchase> torrentPurchaseList = torrentPurchaseService.selectGlxTorrentPurchaseList(filter);

            if (torrentPurchaseList.size() == 0) {
                return error("ERROR_TORRENT_NOT_PURCHASE", announceVO);
            } else {
                glxTorrentPurchase = torrentPurchaseList.get(0);
            }
        }
        if (checkCheating(announceVO, user)) {
            return error("ERROR_CHEATER", announceVO);
        }
        GlxPeer peer = peerService.selectGlxPeerByPeerIdAndInfoHash(announceVO.getPeer_id(), infoHash);
        if (announceVO.getEvent()!= null && announceVO.getEvent().equals("stopped")) {
            //????????????????????????
            if (peer != null && peer.getId() != null) {
                peerService.deleteGlxPeerById(peer.getId());
                return error("Bye", announceVO);
            }
            return error("Bye", announceVO);
        }
        if (peer == null) {
            peer = new GlxPeer();
            peer.setUserId(user.getUserId());
            peer.setInfoHash(infoHash);
            peer.setPeerId(announceVO.getPeer_id());
            peer.setIp(getIp());
            peer.setTorrentId(torrent.getId());
            if (attachment != null) {
                peer.setAttachmentId(attachment.getId());
            }
            peerService.insertGlxPeer(peer);
        }
        if (attachment != null) {
            peer.setAttachmentId(attachment.getId());
        }
        if (announceVO.getPort() > 0) {
            peer.setPort(announceVO.getPort());
        }
        if (announceVO.getUploaded()  > 0) {
            peer.setUploaded(announceVO.getUploaded());
        }
        if (announceVO.getDownloaded()  > 0) {
            peer.setDownloaded(announceVO.getDownloaded());
        }
        if (announceVO.getLeft()  > 0) {
            peer.setLeftSize(announceVO.getLeft());
        }
        if (announceVO.getKey() != null) {
            peer.setKey(announceVO.getKey());
        }
        if (announceVO.getEvent() != null) {
            peer.setEvent(announceVO.getEvent());
        }
        if (!peer.getIp().equalsIgnoreCase(getIp())) {
            peer.setIp(getIp());
        }
        peer.setDownloadSpeed(0L);
        peer.setTorrentId(torrent.getId());
        peer.setUpdateTime(new Date());
//        GlxPeer lastData = redisCache.getCacheObject(peer.getPeerId());
        GlxPeer lastData = peerService.selectGlxPeerByPeerId(peer.getPeerId());
        boolean shouldUpdatePurchaseInfo = false;
        if (glxTorrentPurchase != null && glxTorrentPurchase.getStartTime() == null) {
            glxTorrentPurchase.setStartTime(new Date());
            shouldUpdatePurchaseInfo = true;
        }
        if (announceVO.getEvent() != null && glxTorrentPurchase != null) {
            if (announceVO.getEvent().equals("completed") && glxTorrentPurchase.getFinishTime() == null) {
                glxTorrentPurchase.setFinishTime(new Date());
                peer.setLeftSize(0L);
                long t = (glxTorrentPurchase.getFinishTime().getTime() - glxTorrentPurchase.getStartTime().getTime());
                if (t > 0) {
                    glxTorrentPurchase.setAvgSpeed(torrent.getFileSize() / t);
                    shouldUpdatePurchaseInfo = true;
                }
            } else if (announceVO.getEvent().equals("started") && glxTorrentPurchase.getStartTime() == null) {
                glxTorrentPurchase.setStartTime(new Date());
                shouldUpdatePurchaseInfo = true;
            }
        }
        if (lastData != null && lastData.getUpdateTime() != null) {
            if (peer.getDownloaded() == null) {
                peer.setDownloaded(0L);
            }
            if (peer.getUploaded() == null) {
                peer.setUploaded(0L);
            }
            if (peer.getUploaded() == null) {
                peer.setUploaded(0L);
            }
            if (lastData.getUploaded() == null) {
                lastData.setUploaded(0L);
            }
            if (lastData.getDownloaded() == null) {
                lastData.setDownloaded(0L);
            }
            long tsp = (new Date().getTime() - lastData.getUpdateTime().getTime()) / 1000;
            long downloaded = peer.getDownloaded() - lastData.getDownloaded();
            if (downloaded > 0) {
                peer.setDownloadSpeed(downloaded / tsp);
                torrent.setDownloaded(torrent.getDownloaded() + downloaded);
                // @todo ????????????????????????,????????????????????????????????????
                if (glxTorrentPurchase != null && glxTorrentPurchase.getMaxSpeed() == null) {
                    glxTorrentPurchase.setMaxSpeed(0L);
                }
                if (glxTorrentPurchase != null && glxTorrentPurchase.getMaxSpeed() < peer.getDownloadSpeed()) {
                    glxTorrentPurchase.setMaxSpeed(peer.getDownloadSpeed());
                    shouldUpdatePurchaseInfo = true;
                }
            }
            long uploaded = peer.getUploaded() - lastData.getUploaded();
            if (uploaded > 0) {
                peer.setUploadSpeed(uploaded / tsp);
                torrent.setUploaded(torrent.getUploaded() + uploaded);
            }
            if (uploaded > 0 || downloaded > 0) {
                if (user.getUploaded() == null) {
                    user.setUploaded(0L);
                }
                if (user.getDownloaded() == null) {
                    user.setDownloaded(0L);
                }
                if (attachment == null) {
                    torrentService.updateGlxTorrentCounter(torrent);
                } else {
                    attachment.setUploaded(uploaded);
                    attachment.setDownloaded(downloaded);
                    attachmentService.updateGlxTorrentAttachmentTransmission(attachment);
                }
                user.setUploaded(uploaded);
                user.setDownloaded(downloaded);
                userService.updateUserCounter(user);
            }

            double p = 0d;
            if (attachment != null) {
                GlxPeer filter = new GlxPeer();
                filter.setUserId(user.getUserId());
                filter.setAttachmentId(attachment.getId());
                p = pointsUtil.countUserPoint(attachment.getCreateTime(), (double) attachment.getTorrentSize(), peerService.selectGlxPeerList(filter).size());
            } else {
                List<GlxPeer> pp = peerService.selectGlxPeerByTorrentId(torrent.getId()).stream().filter(x -> { return x.getAttachmentId() == null; }).collect(Collectors.toList());
                p = pointsUtil.countUserPoint(torrent.getCreateTime(), (double) torrent.getFileSize(), pp.size());
            }

            double points = p / 3600 * tsp;//uploaded / ConfigUtil.TRANSMISSION_UNIT;
            if (points > 0 && announceVO.getLeft() == 0) {
                //???????????????????????????
                if (torrent.getUserId().equals(user.getUserId())) {
                    points *= 2;
                }
                String pk = "user_point_counter_" + user.getUserId();
                Map<String, Object> record = redisCache.getCacheMap(pk);
                if (!record.containsKey("point")) {
                    record.put("point", 0d);
                }
                if (!record.containsKey("expire")) {
                    record.put("expire", new Date().getTime() / 1000 + 3600);
                }
                double remain = (double)record.get("point");
                record.put("point", remain + points);
                if ((long)record.get("expire") < new Date().getTime() / 1000) {
                    GlxPointsRecord pointsRecord = new GlxPointsRecord();
                    pointsRecord.setPeerId(peer.getId());
                    pointsRecord.setPoints((double)record.get("point"));
                    pointsRecord.setTorrentId(torrent.getId());
                    pointsRecord.setUserId(user.getUserId());
                    pointsRecord.setRemark("[??????]????????????");
                    pointsRecordService.insertGlxPointsRecord(pointsRecord);
                    record.put("expire", new Date().getTime() / 1000 + 3600);
                    record.put("point",0d);
                }
                redisCache.setCacheMap(pk, record);
                System.out.println("[" + user.getUserName() + "(" + user.getUserId() + ")]???????????????: " + tsp + " ?????????: " + uploaded + " ?????????: " + downloaded + " ?????????: " + points + " ??????: " + remain);
            }
        }
        redisCache.setCacheObject(peer.getPeerId(), peer);

        if (shouldUpdatePurchaseInfo) {
            torrentPurchaseService.updateGlxTorrentPurchase(glxTorrentPurchase);
        }

        peerService.updateGlxPeer(peer);
        HashMap<Object, Object> resp = new HashMap<>();
        List<HashMap<String, Object>> peers = new ArrayList<>();

        List<GlxPeer> peerList = peerService.selectGlxPeerByInfoHash(infoHash);
        if (peerList.size() > announceVO.getNumwant()) {
            if (announceVO.getNumwant() > 0) {
                peerList = peerList.subList(0, announceVO.getNumwant());
            }
        }
        for (GlxPeer p : peerList) {
            if (p.getTorrentId() == null) {
                continue;
            }
            if (!p.getPeerId().equals(peer.getPeerId()) && p.getTorrentId().equals(peer.getTorrentId()) && p.getPort() != null) {
                HashMap<String, Object> peerMap = new HashMap<>();
                peerMap.put("ip", p.getIp());
                peerMap.put("port", p.getPort());
                peerMap.put("peer_id", p.getPeerId());
                peers.add(peerMap);
            }
        }
        resp.put("interval", ConfigUtil.REPORT_TIME);
        resp.put("peers", peers);
        return response(resp, announceVO);
    }


    private String response(HashMap<Object, Object> data, AnnounceVO announceVO) {
//        unlockPeer(announceVO);
        Bencode bencode = new Bencode();
        return new String(bencode.encode(data), bencode.getCharset());
    }

    private String error(String msg, AnnounceVO announceVO) {
        return response(new HashMap<Object, Object>(){
            {
                put("failure reason", new HashMap<String, String>(){
                    {
                        put("type", "string");
                        put("value", msg);
                    }
                });
            }
        }, announceVO);
    }
}
