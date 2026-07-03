package com.aonfine.restaurant.web;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aonfine.auth.service.UserVO;
import com.aonfine.auth.web.AuthController;
import com.aonfine.comment.service.RestaurantCommentService;
import com.aonfine.lunch.service.LunchVoteService;
import com.aonfine.restaurant.service.RestaurantSearchVO;
import com.aonfine.restaurant.service.RestaurantService;
import com.aonfine.restaurant.service.RestaurantVO;

@Controller
public class RestaurantController {

    @Resource(name = "restaurantService")
    private RestaurantService restaurantService;

    @Resource(name = "restaurantCommentService")
    private RestaurantCommentService restaurantCommentService;

    @Resource(name = "lunchVoteService")
    private LunchVoteService lunchVoteService;

    @RequestMapping("/main.do")
    public String main(HttpSession session, Model model) {
        UserVO loginUser = getLoginUser(session);
        model.addAttribute("latestRestaurants", restaurantService.selectLatestRestaurantList(6));
        model.addAttribute("todayLunchTopList", lunchVoteService.selectTodayTopList(3));
        if (loginUser != null) {
            model.addAttribute("myLunchVote", lunchVoteService.selectTodayMyVote(loginUser.getUserId()));
        }
        return "main/index";
    }

    @RequestMapping("/restaurant/list.do")
    public String list(@ModelAttribute("searchVO") RestaurantSearchVO searchVO, HttpSession session, Model model) {
        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
        paginationInfo.setPageSize(10);

        searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
        searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

        int totalCount = restaurantService.selectRestaurantListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totalCount);

        model.addAttribute("restaurantList", restaurantService.selectRestaurantList(searchVO));
        model.addAttribute("paginationInfo", paginationInfo);
        return "restaurant/list";
    }

    @RequestMapping("/restaurant/detail.do")
    public String detail(@RequestParam("restaurantId") Integer restaurantId, Model model) {
        model.addAttribute("restaurant", restaurantService.selectRestaurant(restaurantId));
        model.addAttribute("commentList", restaurantCommentService.selectCommentList(restaurantId));
        return "restaurant/detail";
    }

    @RequestMapping("/restaurant/form.do")
    public String form(HttpSession session, Model model) {
        UserVO loginUser = getLoginUser(session);
        if (loginUser == null) {
            return "redirect:/login.do?returnUrl=/restaurant/form.do";
        }
        RestaurantVO restaurant = new RestaurantVO();
        restaurant.setRestaurantName(loginUser.getUserName());
        model.addAttribute("restaurant", restaurant);
        model.addAttribute("mode", "insert");
        return "restaurant/form";
    }

    @RequestMapping("/restaurant/edit.do")
    public String edit(@RequestParam("restaurantId") Integer restaurantId, HttpSession session, Model model) {
        if (getLoginUser(session) == null) {
            return "redirect:/login.do?returnUrl=/restaurant/edit.do?restaurantId=" + restaurantId;
        }
        model.addAttribute("restaurant", restaurantService.selectRestaurant(restaurantId));
        model.addAttribute("mode", "update");
        return "restaurant/form";
    }

    @RequestMapping("/restaurant/insert.do")
    public String insert(@ModelAttribute RestaurantVO restaurantVO,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) throws IOException {
        UserVO loginUser = getLoginUser(request.getSession(false));
        if (loginUser == null) {
            return "redirect:/login.do?returnUrl=/restaurant/form.do";
        }
        restaurantVO.setRestaurantName(loginUser.getUserName());
        restaurantVO.setCategoryCode("UNCATEGORIZED");
        applyUploadFile(restaurantVO, imageFile, request.getServletContext());
        restaurantService.insertRestaurant(restaurantVO);
        redirectAttributes.addFlashAttribute("message", "맛집이 등록되었습니다.");
        return "redirect:/restaurant/list.do";
    }

    @RequestMapping("/restaurant/update.do")
    public String update(@ModelAttribute RestaurantVO restaurantVO,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) throws IOException {
        UserVO loginUser = getLoginUser(request.getSession(false));
        if (loginUser == null) {
            return "redirect:/login.do?returnUrl=/restaurant/edit.do?restaurantId=" + restaurantVO.getRestaurantId();
        }
        restaurantVO.setRestaurantName(loginUser.getUserName());
        restaurantVO.setCategoryCode("UNCATEGORIZED");
        applyUploadFile(restaurantVO, imageFile, request.getServletContext());
        restaurantService.updateRestaurant(restaurantVO);
        redirectAttributes.addFlashAttribute("message", "맛집 정보가 수정되었습니다.");
        return "redirect:/restaurant/detail.do?restaurantId=" + restaurantVO.getRestaurantId();
    }

    @RequestMapping("/restaurant/delete.do")
    public String delete(@RequestParam("restaurantId") Integer restaurantId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (getLoginUser(session) == null) {
            return "redirect:/login.do?returnUrl=/restaurant/detail.do?restaurantId=" + restaurantId;
        }
        restaurantService.deleteRestaurant(restaurantId);
        redirectAttributes.addFlashAttribute("message", "맛집 정보가 삭제되었습니다.");
        return "redirect:/restaurant/list.do";
    }

    private UserVO getLoginUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object loginUser = session.getAttribute(AuthController.LOGIN_SESSION_KEY);
        return loginUser instanceof UserVO ? (UserVO) loginUser : null;
    }

    private void applyUploadFile(RestaurantVO restaurantVO, MultipartFile imageFile, ServletContext servletContext)
            throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            return;
        }
        if (!StringUtils.hasText(imageFile.getContentType()) || !imageFile.getContentType().startsWith("image/")) {
            throw new IOException("이미지 파일만 업로드할 수 있습니다.");
        }
        String originalName = imageFile.getOriginalFilename();
        String extension = "";
        if (StringUtils.hasText(originalName) && originalName.lastIndexOf('.') > -1) {
            extension = originalName.substring(originalName.lastIndexOf('.'));
        }

        String uploadRelativePath = "/upload/restaurant";
        String realPath = servletContext.getRealPath(uploadRelativePath);
        File uploadDir = new File(realPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String saveName = UUID.randomUUID().toString().replace("-", "") + extension;
        File saveFile = new File(uploadDir, saveName);
        imageFile.transferTo(saveFile);

        restaurantVO.setImagePath(uploadRelativePath + "/" + saveName);
        restaurantVO.setImageOriginalName(originalName);
    }
}
