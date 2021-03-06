package warmer.star.blog.security.oauth2;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.PrincipalExtractor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import warmer.star.blog.mapper.UserMapper;
import warmer.star.blog.mapper.UserRoleMapper;
import warmer.star.blog.model.RoleMenu;
import warmer.star.blog.model.User;
import warmer.star.blog.model.UserInfo;
import warmer.star.blog.model.UserRole;
import warmer.star.blog.util.AppUser;
import warmer.star.blog.util.DateTimeHelper;
import warmer.star.blog.util.RedisUtil;
import warmer.star.blog.util.SpringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractPrincipalExtractor implements PrincipalExtractor {
  @Autowired
  private RedisUtil redisUtil;
  //用户openid
  public abstract User getUserByOpenId(String openId);

  public abstract Integer getSourceTypeOauth2ClientName();
  @Override
  public Object extractPrincipal(Map<String, Object> map) {
    //得到对于的社交平台的openid
    String openId = "";
    /*if(getSourceTypeOauth2ClientName().equals(UserSource.qq)){
      openId = map.get("id").toString();
    }
    else if(getSourceTypeOauth2ClientName().equals(UserSource.gitee)){

    }*/
    openId = map.get("id").toString();
    // Check if we've already registered this uer
    //System.out.println("openId: " + openId);
    User userModel = getUserByOpenId(openId);
    UserMapper userMapper = SpringUtils.getBean(UserMapper.class);
    UserRoleMapper userRoleMapper = SpringUtils.getBean(UserRoleMapper.class);
    if (userModel != null) {
      List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
      List<UserRole> userRoleList = userRoleMapper.getUserRole(userModel.getId());
      for (UserRole userRole : userRoleList) {
        GrantedAuthority grantedAuthority = new SimpleGrantedAuthority(userRole.getRoleItem().getRolecode());
        // 1：此处将权限信息添加到 GrantedAuthority 对象中，在后面进行全权限验证时会使用GrantedAuthority 对象。
        grantedAuthorities.add(grantedAuthority);
      }
      UserInfo userInfo=userMapper.getUserInfo(userModel.getUsername());
      AppUser user = new AppUser(userModel.getUsername(),
              userModel.getPassword() == null ? "" : userModel.getPassword(), grantedAuthorities);
      user.setUserRoles(userRoleList);
      if(userInfo!=null){
        user.setUserId(userModel.getId());
        user.setUserCode(userModel.getUsername());
        user.setAvatar(userInfo.getAvatar());
        user.setNickname(userModel.getUsername());
        redisUtil.remove(userInfo.getUsername());
        redisUtil.set(userInfo.getUsername(), JSON.toJSONString(userInfo),3600);
      }
      user.setUserId(userModel.getId());
      user.setUsername(userModel.getUsername());
      return user;
    }else{
      // If we haven't registered this user yet, create a new one
//      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//      // This Details object exposes a token that allows us to interact with Facebook on this user's behalf
//      String token = ((OAuth2AuthenticationDetails) authentication.getDetails()).getTokenValue();
      userModel = new User();
      BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
      String encode = encoder.encode("123456");
      userModel.setUsername(map.get("login").toString());
      userModel.setOpenid(map.get("id").toString());
      userModel.setPassword(encode);
      userModel.setCreateTime(DateTimeHelper.getNowDate());
      userModel.setSourcetype(getSourceTypeOauth2ClientName());
      // Set the default Roles for users registered via Facebook

      userMapper.addUser(userModel);
      UserInfo userInfo=new UserInfo();
      userInfo.setUserId(userModel.getId());
      userInfo.setAvatar(map.get("avatar_url").toString());
      userInfo.setUsername(map.get("login").toString());
      userInfo.setNickname(map.get("name")!=null?map.get("name").toString():"");
      userInfo.setEmail(map.get("email")!=null?map.get("email").toString():"");
      userMapper.addUserInfo(userInfo);
      //设置角色
      UserRole role=new UserRole();
      role.setUserid(userModel.getId());
      role.setRoleid(2);//访客
      List<UserRole> userRoles=new ArrayList<>();
      userRoles.add(role);
      userRoleMapper.saveUserRole(userRoles);
      List<RoleMenu> roleMenus=new ArrayList<>();
      userRoles=userRoleMapper.getUserRole(userModel.getId());
      //拒绝其他用户登录，但是可以获取他们的信息（哈哈哈）
      //throw new UsernameNotFoundException("用户: " + userModel.getUsername() + " 不属于该博客，拒绝登录");

      List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
      GrantedAuthority grantedAuthority = new SimpleGrantedAuthority("VISITOR");//访客
      // 1：此处将权限信息添加到 GrantedAuthority 对象中，在后面进行全权限验证时会使用GrantedAuthority 对象。
      grantedAuthorities.add(grantedAuthority);
      AppUser user = new AppUser(userModel.getUsername(),
              userModel.getPassword() == null ? "" : userModel.getPassword(), grantedAuthorities);
      user.setUserId(userModel.getId());
      user.setUsername(userModel.getUsername());
      user.setUserRoles(userRoles);
      return user;
    }

  }
}
