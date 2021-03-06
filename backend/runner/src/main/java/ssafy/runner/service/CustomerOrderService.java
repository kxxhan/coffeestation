package ssafy.runner.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.runner.domain.dto.customer.CustomerOrderResponseDto;
import ssafy.runner.domain.dto.customer.order.detail.OrderDetailResDto;
import ssafy.runner.domain.dto.order.OrderMenuRequestDto;
import ssafy.runner.domain.dto.order.OrderRequestDto;
import ssafy.runner.domain.dto.order.OrderResponseDto;
import ssafy.runner.domain.entity.*;
import ssafy.runner.domain.enums.OrderStatus;
import ssafy.runner.domain.repository.*;
import ssafy.runner.firebase.FirebaseCloudMessageService;

import javax.persistence.criteria.Order;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomerOrderService {

    private final OrderRepository orderRepository;
    private final OrderMenuRepository orderMenuRepository;

    private final CustomerRepository customerRepository;
    private final PartnerRepository partnerRepository;

    private final ShopRepository shopRepository;
    private final ShopImageRepository shopImageRepository;
    private final MenuRepository menuRepository;
    private final MenuSizeRepository menuSizeRepository;
    private final ExtraRepository extraRepository;
    private final OrderMenuExtraRepository orderMenuExtraRepository;

    private final FirebaseCloudMessageService firebaseCloudMessageService;

    @Transactional
    public List<CustomerOrderResponseDto> findOrdersByCustomer(String email) {
        Customer customer = customerRepository.findByEmail(email).orElseThrow(NoSuchElementException::new);
        List<Orders> orderList = orderRepository.findByCustomer(customer);
        List<CustomerOrderResponseDto> dtoList = new ArrayList<>();
        orderList.forEach(o->{
            Long shopId = o.getShop().getId();
            String shopImgUrl = shopImageRepository.findByShopIdAndIndex(shopId, 1).orElseThrow(NoSuchElementException::new);
//            String shopImgUrl = shopImage.getImgUrl();
            dtoList.add(CustomerOrderResponseDto.of(o, shopImgUrl));
        });
        Collections.reverse(dtoList);
        return dtoList;
    }

    // ?????? ?????? ?????? ????????????
    @Transactional
    public OrderDetailResDto findOneOrder(String email, Long orderId) {
        // orderId??? ???????????? ???????????? ????????????
        List<OrderMenu> orderMenuList = orderMenuRepository.findOneFetched(orderId);


//        System.out.println("===============????????????====================");
        orderMenuList.forEach(orderMenu->{
//            System.out.println("===============????????????-inner====================");
            Orders order = orderMenu.getOrder();
            List<OrderMenuExtra> orderMenuExtras = orderMenu.getOrderMenuExtras();
            Menu menu = orderMenu.getMenu();
            MenuSize menuSize = orderMenu.getMenuSize();

//            System.out.println("orderMenuList = " + orderMenuList);
//            System.out.println("orderMenu = " + orderMenu);
//            System.out.println("order = " + order);
//            System.out.println("orderMenuExtras = " + orderMenuExtras);
//            System.out.println("menu = " + menu);
//            System.out.println("menuSize = " + menuSize);
//            // ??????????????? N+1??? ????????? ??? ?????? ??????
//            System.out.println("Size = " + menuSize.getSize().getId());
//            System.out.println("Size = " + menuSize.getSize().getType());
            orderMenuExtras.forEach(ome->{
//                System.out.println("orderMenuExtra = "+ome.getExtra().getId());
//                System.out.println("orderMenuExtra = "+ome.getExtra().getName());
            });
            // N+1??? ????????? ??? ?????? ?????? ??????

//            System.out.println("===============????????????-inner====================");

        });

        return OrderDetailResDto.of(orderMenuList);
    }

    @Transactional
    public OrderResponseDto order(String email, Long shopId, OrderRequestDto params) {

        // Order ?????? ?????? ??? ??????
        Customer customer = customerRepository.findByEmail(email).orElseThrow(NoSuchElementException::new);
        Shop shop = shopRepository.findById(shopId).orElseThrow(NoSuchElementException::new);
        Orders orders = new Orders(shop, customer, OrderStatus.ORDERED, 0, params.getRequest());
        orderRepository.save(orders);
        int totalPrice = 0;

        List<OrderMenuRequestDto> orderMenuListDto = params.getOrderMenuList();
        for (OrderMenuRequestDto orderMenuDto : orderMenuListDto) {
            // orderMenuDto??? ??????????????? OrderMenu ?????? ??????
            Menu menu = menuRepository.findById(orderMenuDto.getMenuId()).orElseThrow(NoSuchElementException::new);
            MenuSize menuSize = menuSizeRepository.findById(orderMenuDto.getMenuSizeId()).orElseThrow(NoSuchElementException::new);
            OrderMenu orderMenu = new OrderMenu(orders, menu, menuSize, orderMenuDto.getQuantity(),0);
            orderMenuRepository.save(orderMenu);
            int orderMenuPrice = menu.getPrice();

            for (Long extraId : orderMenuDto.getExtraIdList()) {
                // ?????? extra ??????, OrderMenuExtra ?????? ?????? ??? ??????
                Extra extra = extraRepository.findById(extraId).orElseThrow(NoSuchElementException::new);
                OrderMenuExtra orderMenuExtra = new OrderMenuExtra(orderMenu, extra);
                orderMenuExtraRepository.save(orderMenuExtra);
                orderMenuPrice += extra.getPrice();
            }

            // orderMenu
            orderMenuPrice = orderMenuPrice * orderMenuDto.getQuantity();  // quantity ????????????
            orderMenu.modifyOrderMenuPrice(orderMenuPrice); // orderMenu price ??????/????????????
            totalPrice += orderMenuPrice;  // order ????????????(totalPrice)??? orderMenu price ????????????
        }

        // order totalPrice ??????
        orders.modifyOrderPrice(totalPrice);

        // OrderResponseDto ?????? ??????
        return new OrderResponseDto(orders);
    }

    public void paidFcm(Long orderId) throws IOException {
        System.out.println("?????? ?????????!");
        // shop??? fetch join?????? ?????? ????????????
        Orders order = orderRepository.findOrderNShopById(orderId).orElseThrow(NoSuchElementException::new);
        List<OrderMenu> menuList = orderMenuRepository.findOneSimpleById(orderId);
        int menuListSize = menuList.size();
        String menuName = menuList.get(0).getMenu().getName();

        Long shopId = order.getShop().getId();
        Shop shop = shopRepository.findShopNPartnerById(shopId).orElseThrow(NoSuchElementException::new);
        String firebaseToken = shop.getPartner().getFirebaseToken();
//        String firebaseToken = partner.getFirebaseToken();
        firebaseCloudMessageService.sendMessageTo(firebaseToken, "COFFEE_STATION", menuName + " ??? " + menuListSize + "?????? ????????? ?????????????????????.", orderId);
    }
}
